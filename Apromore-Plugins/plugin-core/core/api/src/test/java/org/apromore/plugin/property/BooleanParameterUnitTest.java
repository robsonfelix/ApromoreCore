/*
 * Copyright © 2009-2018 The Apromore Initiative.
 *
 * This file is part of "Apromore".
 *
 * "Apromore" is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * "Apromore" is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program.
 * If not, see <http://www.gnu.org/licenses/lgpl-3.0.html>.
 */

package org.apromore.plugin.property;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BooleanParameterUnitTest {

    @Test
    public void test() {
        PluginParameterType<Boolean> prop = new PluginParameterType<Boolean>("t1", "test", "test", false, false);
        assertFalse(prop.getValue());
        assertFalse(prop.getValue());

        PluginParameterType<Boolean> prop3 = new PluginParameterType<Boolean>("t1", "test", Boolean.class, "test", false);
        prop3.setValue(new Boolean(true));
        assertTrue(prop3.getValue());
        assertEquals(Boolean.class, prop3.getValueType());
    }

}
