/*
 * Copyright 2008-2009 LinkedIn, Inc
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package voldemort.store;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import voldemort.cluster.Node;
import voldemort.routing.RoutingStrategy;
import voldemort.serialization.Serializer;
import voldemort.serialization.SerializerDefinition;
import voldemort.serialization.SerializerFactory;
import voldemort.utils.ByteArray;
import voldemort.utils.ClosableIterator;
import voldemort.utils.Pair;
import voldemort.versioning.Version;
import voldemort.versioning.Versioned;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Check that the given key is valid
 * 
 * @author jay
 * 
 */
public class StoreUtils {

    private static Logger logger = Logger.getLogger(StoreUtils.class);

    public static void assertValidKeys(Iterable<?> keys) {
        if(keys == null)
            throw new IllegalArgumentException("Keys cannot be null.");
        for(Object key: keys)
            assertValidKey(key);
    }

    public static <K> void assertValidKey(K key) {
        if(key == null)
            throw new IllegalArgumentException("Key cannot be null.");
    }

    /**
     * Implements get by delegating to getAll.
     */
    public static <K, V> List<Versioned<V>> get(Store<K, V> storageEngine, K key) {
        Map<K, List<Versioned<V>>> result = storageEngine.getAll(Collections.singleton(key));
        if(result.size() > 0)
            return result.get(key);
        else
            return Collections.emptyList();
    }

    /**
     * Implements getAll by delegating to get.
     */
    public static <K, V> Map<K, List<Versioned<V>>> getAll(Store<K, V> storageEngine,
                                                           Iterable<K> keys) {
        Map<K, List<Versioned<V>>> result = newEmptyHashMap(keys);
        for(K key: keys) {
            List<Versioned<V>> value = storageEngine.get(key);
            if(!value.isEmpty())
                result.put(key, value);
        }
        return result;
    }

    /**
     * Returns an empty map with expected size matching the iterable size if
     * it's of type Collection. Otherwise, an empty map with the default size is
     * returned.
     */
    public static <K, V> HashMap<K, V> newEmptyHashMap(Iterable<?> iterable) {
        if(iterable instanceof Collection<?>)
            return Maps.newHashMapWithExpectedSize(((Collection<?>) iterable).size());
        return Maps.newHashMap();
    }

    /**
     * Closes an InputStream and logs a potential error instead of re-throwing
     * the exception. If a {@code null} stream is passed, this method is a
     * no-op.
     * 
     * This is typically used in finally blocks to prevent an exception thrown
     * during close from hiding an exception thrown inside the try.
     * 
     * @param stream the InputStream to close, may be null.
     */
    public static void close(InputStream stream) {
        if(stream != null) {
            try {
                stream.close();
            } catch(IOException e) {
                logger.error("Error closing stream", e);
            }
        }
    }

    /**
     * Check if the current node is part of routing request based on cluster.xml
     * or throw an exception.
     * 
     * @param key
     * @param routingStrategy
     * @param currentNodeId
     */
    public static void assertValidMetadata(ByteArray key,
                                           RoutingStrategy routingStrategy,
                                           Node currentNode) {
        List<Node> nodes = routingStrategy.routeRequest(key.get());
        for(Node node: nodes) {
            if(node.getId() == currentNode.getId()) {
                return;
            }
        }

        throw new InvalidMetadataException("client attempt accessing key belonging to partition:"
                                           + routingStrategy.getPartitionList(key.get())
                                           + " at Node:" + currentNode);
    }

    public static <V> List<Version> getVersions(List<Versioned<V>> versioneds) {
        List<Version> versions = Lists.newArrayListWithCapacity(versioneds.size());
        for(Versioned<?> versioned: versioneds)
            versions.add(versioned.getVersion());
        return versions;
    }

    public static <K, V> ClosableIterator<K> keys(final ClosableIterator<Pair<K, V>> values) {
        return new ClosableIterator<K>() {

            public void close() {
                values.close();
            }

            public boolean hasNext() {
                return values.hasNext();
            }

            public K next() {
                Pair<K, V> value = values.next();
                if(value == null)
                    return null;
                return value.getFirst();
            }

            public void remove() {
                values.remove();
            }

        };
    }

    /**
     * This is a temporary measure until we have a type-safe solution for
     * retrieving serializers from a SerializerFactory. It avoids warnings all
     * over the codebase while making it easy to verify who calls it.
     */
    @SuppressWarnings("unchecked")
    public static <T> Serializer<T> unsafeGetSerializer(SerializerFactory serializerFactory,
                                                        SerializerDefinition serializerDefinition) {
        return (Serializer<T>) serializerFactory.getSerializer(serializerDefinition);
    }

    /**
     * Get a store definition from the given list of store definitions
     * 
     * @param list A list of store definitions
     * @param name The name of the store
     * @return The store definition
     */
    public static StoreDefinition getStoreDef(List<StoreDefinition> list, String name) {
        for(StoreDefinition def: list)
            if(def.getName().equals(name))
                return def;
        return null;
    }
}
