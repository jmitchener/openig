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
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2010-2011 ApexIdentity Inc.
 * Portions Copyright 2011-2014 ForgeRock AS.
 */

package org.forgerock.openig.resolver;

import java.util.Map;

import org.forgerock.http.util.UnmodifiableCollection;

/**
 * Resolves {@link Map} objects.
 */
@SuppressWarnings("rawtypes")
public class MapResolver implements Resolver {

    @Override
    public Class<?> getKey() {
        return Map.class;
    }

    @Override
    public Object get(Object object, Object element) {
        if (object instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) object;
            if (map.containsKey(element)) {
                return map.get(element);
            }
        }
        return Resolver.UNRESOLVED;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object put(Object object, Object element, Object value) {
        if (object instanceof Map && !(object instanceof UnmodifiableCollection)) {
            Map map = (Map) object;
            try {
                return map.put(element, value);
            } catch (UnsupportedOperationException uoe) {
                // ignore failed attempts to write to read-only map
            }
        }
        return Resolver.UNRESOLVED;
    }
}
