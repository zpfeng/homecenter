/* Copyright (c) 2001-2014, The HSQL Development Group
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * Neither the name of the HSQL Development Group nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL HSQL DEVELOPMENT GROUP, HSQLDB.ORG,
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */


package third.hsqldb.lib;

import third.hsqldb.map.BaseHashMap;

/**
 *
 * @author Fred Toussi (fredt@users dot sourceforge.net)
 * @version 2.3.3
 * @since 2.0.0
 */
public class OrderedIntKeyHashMap extends BaseHashMap {

    Set        keySet;
    Collection values;

    public OrderedIntKeyHashMap() {
        this(8);
    }

    public OrderedIntKeyHashMap(int initialCapacity)
    throws IllegalArgumentException {

        super(initialCapacity, BaseHashMap.intKeyOrValue,
              BaseHashMap.objectKeyOrValue, false);

        isList = true;
    }

    public int getKey(int lookup, int def) {

        if (lookup >= 0 && lookup < size()) {
            return this.intKeyTable[lookup];
        }

        return def;
    }

    public Object getValue(int lookup) {

        if (lookup >= 0 && lookup < size()) {
            return this.objectValueTable[lookup];
        }

        return null;
    }

    public Object get(int key) {

        int lookup = getLookup(key);

        if (lookup != -1) {
            return objectValueTable[lookup];
        }

        return null;
    }

    public Object put(int key, Object value) {
        return super.addOrRemove(key, value, null, false);
    }

    public boolean containsValue(Object value) {
        return super.containsValue(value);
    }

    public Object remove(int key) {

        int lookup = getLookup(key);

        if (lookup < 0) {
            return null;
        }

        Object returnValue = super.addOrRemove(key, null, null, true);

        removeRow(lookup);

        return returnValue;
    }

    public Object removeKeyAndValue(int index)
    throws IndexOutOfBoundsException {

        checkRange(index);

        return remove(intKeyTable[index]);
    }

    public boolean containsKey(int key) {
        return super.containsKey(key);
    }

    public void valuesToArray(Object[] array) {

        Iterator it = values().iterator();
        int      i  = 0;

        while (it.hasNext()) {
            array[i] = it.next();

            i++;
        }
    }

    public Set keySet() {

        if (keySet == null) {
            keySet = new KeySet();
        }

        return keySet;
    }

    public Collection values() {

        if (values == null) {
            values = new Values();
        }

        return values;
    }

    private void checkRange(int i) {

        if (i < 0 || i >= size()) {
            throw new IndexOutOfBoundsException();
        }
    }

    class KeySet implements Set {

        public Iterator iterator() {
            return OrderedIntKeyHashMap.this.new BaseHashIterator(true);
        }

        public int size() {
            return OrderedIntKeyHashMap.this.size();
        }

        public boolean contains(Object o) {
            throw new UnsupportedOperationException();
        }

        public Object get(Object key) {
            throw new UnsupportedOperationException();
        }

        public boolean add(Object value) {
            throw new UnsupportedOperationException();
        }

        public boolean addAll(Collection c) {
            throw new UnsupportedOperationException();
        }

        public boolean remove(Object o) {
            throw new UnsupportedOperationException();
        }

        public boolean isEmpty() {
            return size() == 0;
        }

        public void clear() {
            OrderedIntKeyHashMap.this.clear();
        }
    }

    class Values implements Collection {

        public Iterator iterator() {
            return OrderedIntKeyHashMap.this.new BaseHashIterator(false);
        }

        public int size() {
            return OrderedIntKeyHashMap.this.size();
        }

        public boolean contains(Object o) {
            throw new UnsupportedOperationException();
        }

        public boolean add(Object value) {
            throw new UnsupportedOperationException();
        }

        public boolean addAll(Collection c) {
            throw new UnsupportedOperationException();
        }

        public boolean remove(Object o) {
            throw new UnsupportedOperationException();
        }

        public boolean isEmpty() {
            return size() == 0;
        }

        public void clear() {
            OrderedIntKeyHashMap.this.clear();
        }
    }
}
