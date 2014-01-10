/**
 * Copyright (c) 2014 openHAB UG (haftungsbeschränkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.smarthome.model.validation;

import org.eclipse.xtext.validation.Check;
import org.eclipse.smarthome.model.sitemap.Frame;
import org.eclipse.smarthome.model.sitemap.LinkableWidget;
import org.eclipse.smarthome.model.sitemap.Sitemap;
import org.eclipse.smarthome.model.sitemap.SitemapPackage;
import org.eclipse.smarthome.model.sitemap.Widget;
import org.eclipse.smarthome.model.validation.AbstractSitemapJavaValidator;
 

public class SitemapJavaValidator extends AbstractSitemapJavaValidator {

	@Check
	public void checkFramesInFrame(Frame frame) {
		for(Widget w : frame.getChildren()) {
			if(w instanceof Frame) {
				error("Frames must not contain other frames", SitemapPackage.Literals.FRAME.getEStructuralFeature(SitemapPackage.FRAME__CHILDREN));
				break;
			}
		}
	}

	@Check
	public void checkFramesInWidgetList(Sitemap sitemap) {
		boolean containsFrames = false;
		boolean containsOtherWidgets = false;
		for(Widget w : sitemap.getChildren()) {
			if(w instanceof Frame) {
				containsFrames = true;
			} else {
				containsOtherWidgets = true;
			}
			if(containsFrames && containsOtherWidgets) {
				error("Sitemap should contain either only frames or none at all", SitemapPackage.Literals.FRAME.getEStructuralFeature(SitemapPackage.SITEMAP__CHILDREN));
				break;
			}
		}
	}

	@Check
	public void checkFramesInWidgetList(LinkableWidget widget) {
		if(widget instanceof Frame) {
			// we have a dedicated check for frames in place
			return;
		}
		boolean containsFrames = false;
		boolean containsOtherWidgets = false;
		for(Widget w : widget.getChildren()) {
			if(w instanceof Frame) {
				containsFrames = true;
			} else {
				containsOtherWidgets = true;
			}
			if(containsFrames && containsOtherWidgets) {
				error("Linkable widget should contain either only frames or none at all", SitemapPackage.Literals.FRAME.getEStructuralFeature(SitemapPackage.LINKABLE_WIDGET__CHILDREN));
				break;
			}
		}
	}

}
