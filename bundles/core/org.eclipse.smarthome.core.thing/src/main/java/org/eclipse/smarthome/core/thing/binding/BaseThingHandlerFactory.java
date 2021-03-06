/**
 * Copyright (c) 2014-2017 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.smarthome.core.thing.binding;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.config.core.ConfigDescriptionRegistry;
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.config.core.status.ConfigStatusProvider;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.firmware.FirmwareUpdateHandler;
import org.eclipse.smarthome.core.thing.type.ThingType;
import org.eclipse.smarthome.core.thing.type.ThingTypeRegistry;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.ComponentContext;
import org.osgi.util.tracker.ServiceTracker;

/**
 * The {@link BaseThingHandlerFactory} provides a base implementation for the {@link ThingHandlerFactory} interface.
 * <p>
 * It is recommended to extend this abstract base class, because it covers a lot of common logic.
 * <p>
 *
 * @author Dennis Nobel - Initial contribution
 * @author Benedikt Niehues - fix for Bug https://bugs.eclipse.org/bugs/show_bug.cgi?id=445137 considering
 *         default values
 * @author Thomas Höfer - added config status provider and firmware update handler service registration
 * @author Stefan Bußweiler - API changes due to bridge/thing life cycle refactoring, removed OSGi service registration
 *         for thing handlers
 */
public abstract class BaseThingHandlerFactory implements ThingHandlerFactory {

    protected BundleContext bundleContext;

    private Map<String, ServiceRegistration<ConfigStatusProvider>> configStatusProviders = new ConcurrentHashMap<>();
    private Map<String, ServiceRegistration<FirmwareUpdateHandler>> firmwareUpdateHandlers = new ConcurrentHashMap<>();

    private ServiceTracker<ThingTypeRegistry, ThingTypeRegistry> thingTypeRegistryServiceTracker;
    private ServiceTracker<ConfigDescriptionRegistry, ConfigDescriptionRegistry> configDescriptionRegistryServiceTracker;

    /**
     * Initializes the {@link BaseThingHandlerFactory}. If this method is overridden by a sub class, the implementing
     * method must call <code>super.activate(componentContext)</code> first.
     *
     * @param componentContext
     *            component context (must not be null)
     */
    protected void activate(ComponentContext componentContext) {
        this.bundleContext = componentContext.getBundleContext();
        thingTypeRegistryServiceTracker = new ServiceTracker<>(bundleContext, ThingTypeRegistry.class.getName(), null);
        thingTypeRegistryServiceTracker.open();
        configDescriptionRegistryServiceTracker = new ServiceTracker<>(bundleContext,
                ConfigDescriptionRegistry.class.getName(), null);
        configDescriptionRegistryServiceTracker.open();
    }

    /**
     * Disposes the {@link BaseThingHandlerFactory}. If this method is overridden by a sub class, the implementing
     * method must call <code>super.deactivate(componentContext)</code> first.
     *
     * @param componentContext
     *            component context (must not be null)
     */
    protected void deactivate(ComponentContext componentContext) {
        for (ServiceRegistration<ConfigStatusProvider> serviceRegistration : configStatusProviders.values()) {
            if (serviceRegistration != null) {
                serviceRegistration.unregister();
            }
        }
        for (ServiceRegistration<FirmwareUpdateHandler> serviceRegistration : firmwareUpdateHandlers.values()) {
            if (serviceRegistration != null) {
                serviceRegistration.unregister();
            }
        }
        thingTypeRegistryServiceTracker.close();
        configDescriptionRegistryServiceTracker.close();
        configStatusProviders.clear();
        firmwareUpdateHandlers.clear();
        bundleContext = null;
    }

    @Override
    public ThingHandler registerHandler(Thing thing) {
        ThingHandler thingHandler = createHandler(thing);
        if (thingHandler == null) {
            throw new IllegalStateException(this.getClass().getSimpleName()
                    + " could not create a handler for the thing '" + thing.getUID() + "'.");
        }
        if ((thing instanceof Bridge) && !(thingHandler instanceof BridgeHandler)) {
            throw new IllegalStateException(
                    "Created handler of bridge '" + thing.getUID() + "' must implement the BridgeHandler interface.");
        }
        setHandlerContext(thingHandler);
        registerConfigStatusProvider(thing, thingHandler);
        registerFirmwareUpdateHandler(thing, thingHandler);
        return thingHandler;
    }

    /**
     * Creates a {@link ThingHandler} for the given thing.
     *
     * @param thing the thing
     * @return thing the created handler
     */
    protected abstract ThingHandler createHandler(Thing thing);

    private void setHandlerContext(ThingHandler thingHandler) {
        if (thingHandler instanceof BaseThingHandler) {
            if (bundleContext == null) {
                throw new IllegalStateException(
                        "Base thing handler factory has not been properly initialized. Did you forget to call super.activate()?");
            }
            ((BaseThingHandler) thingHandler).setBundleContext(bundleContext);
        }
    }

    private void registerConfigStatusProvider(Thing thing, ThingHandler thingHandler) {
        if (thingHandler instanceof ConfigStatusProvider) {
            ServiceRegistration<ConfigStatusProvider> serviceRegistration = registerAsService(thingHandler,
                    ConfigStatusProvider.class);
            configStatusProviders.put(thing.getUID().getAsString(), serviceRegistration);
        }
    }

    private void registerFirmwareUpdateHandler(Thing thing, ThingHandler thingHandler) {
        if (thingHandler instanceof FirmwareUpdateHandler) {
            ServiceRegistration<FirmwareUpdateHandler> serviceRegistration = registerAsService(thingHandler,
                    FirmwareUpdateHandler.class);
            firmwareUpdateHandlers.put(thing.getUID().getAsString(), serviceRegistration);
        }
    }

    private <T> ServiceRegistration<T> registerAsService(ThingHandler thingHandler, Class<T> type) {
        @SuppressWarnings("unchecked")
        ServiceRegistration<T> serviceRegistration = (ServiceRegistration<T>) bundleContext
                .registerService(type.getName(), thingHandler, null);
        return serviceRegistration;
    }

    @Override
    public void unregisterHandler(Thing thing) {
        ThingHandler thingHandler = thing.getHandler();
        if (thingHandler != null) {
            removeHandler(thingHandler);
            unsetBundleContext(thingHandler);
        }
        unregisterConfigStatusProvider(thing);
        unregisterFirmwareUpdateHandler(thing);
    }

    /**
     * This method is called when a thing handler should be removed. The
     * implementing caller can override this method to release specific
     * resources.
     *
     * @param thingHandler
     *            thing handler to be removed
     */
    protected void removeHandler(@NonNull ThingHandler thingHandler) {
        // can be overridden
    }

    private void unsetBundleContext(ThingHandler thingHandler) {
        if (thingHandler instanceof BaseThingHandler) {
            ((BaseThingHandler) thingHandler).unsetBundleContext(bundleContext);
        }
    }

    private void unregisterConfigStatusProvider(Thing thing) {
        ServiceRegistration<ConfigStatusProvider> serviceRegistration = configStatusProviders
                .remove(thing.getUID().getAsString());
        if (serviceRegistration != null) {
            serviceRegistration.unregister();
        }
    }

    private void unregisterFirmwareUpdateHandler(Thing thing) {
        ServiceRegistration<FirmwareUpdateHandler> serviceRegistration = firmwareUpdateHandlers
                .remove(thing.getUID().getAsString());
        if (serviceRegistration != null) {
            serviceRegistration.unregister();
        }
    }

    @Override
    public void removeThing(ThingUID thingUID) {
        // can be overridden
    }

    /**
     * Returns the {@link ThingType} which is represented by the given {@link ThingTypeUID}.
     *
     * @param thingTypeUID the unique id of the thing type
     * @return the thing type represented by the given unique id
     */
    protected @Nullable ThingType getThingTypeByUID(@NonNull ThingTypeUID thingTypeUID) {
        if (thingTypeRegistryServiceTracker == null) {
            throw new IllegalStateException(
                    "Base thing handler factory has not been properly initialized. Did you forget to call super.activate()?");
        }
        ThingTypeRegistry thingTypeRegistry = thingTypeRegistryServiceTracker.getService();
        if (thingTypeRegistry != null) {
            return thingTypeRegistry.getThingType(thingTypeUID);
        }
        return null;
    }

    /**
     * Creates a thing based on given thing type uid.
     *
     * @param thingTypeUID
     *            thing type uid (can not be null)
     * @param configuration
     *            (can not be null)
     * @param thingUID
     *            thingUID (can not be null)
     * @return thing (can be null, if thing type is unknown)
     */
    protected @Nullable Thing createThing(@NonNull ThingTypeUID thingTypeUID, @NonNull Configuration configuration,
            @NonNull ThingUID thingUID) {
        return createThing(thingTypeUID, configuration, thingUID, null);
    }

    /**
     * Creates a thing based on given thing type uid.
     *
     * @param thingTypeUID
     *            thing type uid (must not be null)
     * @param thingUID
     *            thingUID (can be null)
     * @param configuration
     *            (must not be null)
     * @param bridgeUID
     *            (can be null)
     * @return thing (can be null, if thing type is unknown)
     */
    @Override
    public Thing createThing(ThingTypeUID thingTypeUID, Configuration configuration, ThingUID thingUID,
            ThingUID bridgeUID) {
        ThingUID effectiveUID = thingUID != null ? thingUID : ThingFactory.generateRandomThingUID(thingTypeUID);
        ThingType thingType = getThingTypeByUID(thingTypeUID);
        if (thingType != null) {
            Thing thing = ThingFactory.createThing(thingType, effectiveUID, configuration, bridgeUID,
                    getConfigDescriptionRegistry());
            return thing;
        } else {
            return null;
        }
    }

    protected ConfigDescriptionRegistry getConfigDescriptionRegistry() {
        if (configDescriptionRegistryServiceTracker == null) {
            throw new IllegalStateException(
                    "Config Description Registry has not been properly initialized. Did you forget to call super.activate()?");
        }
        return configDescriptionRegistryServiceTracker.getService();
    }

}