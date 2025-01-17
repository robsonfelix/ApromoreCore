/*-
 * #%L
 * This file is part of "Apromore Core".
 * %%
 * Copyright (C) 2018 - 2022 Apromore Pty Ltd.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */
/*
 * @(#)CellHandle.java	1.0 03-JUL-04
 * 
 * Copyright (c) 2001-2004 Gaudenz Alder
 *  
 */
package org.apromore.jgraph.graph;

import java.awt.Graphics;
import java.awt.event.MouseEvent;

/**
 * Defines the requirements for objects that may be used as handles.
 * Handles are used to interactively manipulate a cell's appearance.
 *
 * @version 1.0 1/1/02
 * @author Gaudenz Alder
 */

public interface CellHandle {

	/**
	 * Paint the handle on the given graphics object once.
	 *
	 * @param g       the graphics object to paint the handle on
	 */
	void paint(Graphics g);

	/**
	 * Paint the handle on the given graphics object during mouse
	 * operations.
	 *
	 * @param g       the graphics object to paint the handle on
	 */
	void overlay(Graphics g);

	/**
	 * Messaged when the mouse is moved.
	 *
	 * @param event   the mouse event to be processed
	 */
	void mouseMoved(MouseEvent event);

	/**
	 * Messaged when a mouse button is pressed.
	 *
	 * @param event   the mouse event to be processed
	 */
	void mousePressed(MouseEvent event);

	/**
	 * Messaged when the user drags the selection.
	 * The Controller is responsible to determine whether the mouse is
	 * inside the parent graph or not.
	 *
	 * @param event   the drag event to be processed
	 */
	void mouseDragged(MouseEvent event);

	/**
	 * Messaged when the drag operation has
	 * terminated with a drop.
	 *
	 * @param event   the drop event to be processed
	 */
	void mouseReleased(MouseEvent event);

}
