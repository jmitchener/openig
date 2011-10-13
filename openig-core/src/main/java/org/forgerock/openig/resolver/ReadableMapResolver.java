/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyrighted [year] [name of copyright owner]".
 *
 * Copyright © 2010–2011 ApexIdentity Inc. All rights reserved.
 * Portions Copyrighted 2011 ForgeRock AS.
 */

package org.forgerock.openig.resolver;

// Java Standard Edition
import java.util.Collections;
import java.util.Set;

// OpenIG Core
import org.forgerock.openig.util.ReadableMap;

/**
 * Resolves {@link ReadableMap} objects. This implementation uses duck-typing to attempt
 * access to the object. It will successfully resolve the object if it exposes the methods
 * defined in the {@code ReadableMap} interface.
 *
 * @author Paul C. Bryan
 */
public class ReadableMapResolver implements Resolver {

    @Override
    public Class getKey() {
        return ReadableMap.class;
    }

    @Override
    public Object get(Object object, Object element) {
        try {
            return ReadableMap.DUCK.cast(object).get(element);
        } catch (ClassCastException cce) {
            return Resolver.UNRESOLVED; // cannot be duck-typed into a readable map
        }
    }

    @Override
    public Object put(Object object, Object element, Object value) {
        return Resolver.UNRESOLVED; // read-only
    }

    @Override
    public Object remove(Object object, Object element) {
        return Resolver.UNRESOLVED; // read-only
    }

    @Override
    public boolean containsKey(Object object, Object element) {
        return false; // non-enumerable map
    }

    @Override
    public Set<? extends Object> keySet(Object object) {
        return Collections.emptySet(); // non-enumerable map
    }
}
