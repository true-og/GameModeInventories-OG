package me.eccentric_nz.gamemodeinventories.JSON;

/*
Copyright (c) 2002 JSON.org

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

The Software shall be used for Good, not Evil.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

// An unordered collection of name/value pairs whose external form is JSON text in
// curly braces. get methods throw if a value is missing; opt methods return a default.
public class JSONObject {

    // A sentinel less ambiguous than Java's null: NULL.equals(null) returns
    // true, and NULL.toString() returns "null".
    public static final Object NULL = new Null();
    private static final int keyPoolSize = 100;
    // Key pool: like string interning but without permanently tying up memory,
    // avoiding duplicate key strings. Used by put(String, Object).
    private static HashMap keyPool = new HashMap(keyPoolSize);
    private final Map map;

    public JSONObject() {

        map = new HashMap();

    }

    // Construct a JSONObject from a subset of another JSONObject, copying the
    // given keys. Missing keys are ignored.
    public JSONObject(JSONObject jo, String[] names) {

        this();
        for (int i = 0; i < names.length; i += 1) {

            try {

                putOnce(names[i], jo.opt(names[i]));

            } catch (Exception ignore) {

            }

        }

    }

    // Construct a JSONObject from a JSONTokener; throws on syntax error or
    // duplicated key.
    public JSONObject(JSONTokener x) throws JSONException {

        this();
        char c;
        String key;

        if (x.nextClean() != '{') {

            throw x.syntaxError("A JSONObject text must begin with '{'");

        }

        for (;;) {

            c = x.nextClean();
            switch (c) {

                case 0:
                    throw x.syntaxError("A JSONObject text must end with '}'");
                case '}':
                    return;
                default:
                    x.back();
                    key = x.nextValue().toString();

            }

            // The key is followed by ':'.
            c = x.nextClean();
            if (c != ':') {

                throw x.syntaxError("Expected a ':' after a key");

            }

            putOnce(key, x.nextValue());

            // Pairs are separated by ','.
            switch (x.nextClean()) {

                case ';':
                case ',':
                    if (x.nextClean() == '}') {

                        return;

                    }
                    x.back();
                    break;
                case '}':
                    return;
                default:
                    throw x.syntaxError("Expected a ',' or '}'");

            }

        }

    }

    // Construct a JSONObject from a Map; null values are skipped.
    public JSONObject(Map map) {

        this.map = new HashMap();
        if (map != null) {

            Iterator i = map.entrySet().iterator();
            while (i.hasNext()) {

                Map.Entry e = (Map.Entry) i.next();
                Object value = e.getValue();
                if (value != null) {

                    this.map.put(e.getKey(), wrap(value));

                }

            }

        }

    }

    // Construct a JSONObject from an object's bean getters: each public no-arg
    // get/is method's decapitalized name (minus the prefix) becomes the key.
    public JSONObject(Object bean) {

        this();
        populateMap(bean);

    }

    // Construct a JSONObject from an object's public fields named in names.
    // Keys that are not found or not visible are skipped.
    public JSONObject(Object object, String names[]) {

        this();
        Class c = object.getClass();
        for (int i = 0; i < names.length; i += 1) {

            String name = names[i];
            try {

                putOpt(name, c.getField(name).get(object));

            } catch (Exception ignore) {

            }

        }

    }

    // Construct a JSONObject from a source JSON text string; throws on
    // syntax error or duplicated key.
    public JSONObject(String source) throws JSONException {

        this(new JSONTokener(source));

    }

    // Construct a JSONObject from a ResourceBundle, nesting objects along dotted
    // key paths.
    public JSONObject(String baseName, Locale locale) throws JSONException {

        this();
        ResourceBundle bundle = ResourceBundle.getBundle(baseName, locale,
                Thread.currentThread().getContextClassLoader());

        // Iterate through the keys in the bundle.
        Enumeration keys = bundle.getKeys();
        while (keys.hasMoreElements()) {

            Object key = keys.nextElement();
            if (key instanceof String) {

                // Ensure a nested JSONObject exists for each path segment except the last,
                // then add the value under the last segment's name.
                String[] path = ((String) key).split("\\.");
                int last = path.length - 1;
                JSONObject target = this;
                for (int i = 0; i < last; i += 1) {

                    String segment = path[i];
                    JSONObject nextTarget = target.optJSONObject(segment);
                    if (nextTarget == null) {

                        nextTarget = new JSONObject();
                        target.put(segment, nextTarget);

                    }

                    target = nextTarget;

                }

                target.put(path[last], bundle.getString((String) key));

            }

        }

    }

    // Produce a string from a double; returns "null" if the number is not finite.
    public static String doubleToString(double d) {

        if (Double.isInfinite(d) || Double.isNaN(d)) {

            return "null";

        }

        // Shave off trailing zeros and decimal point, if possible.
        String string = Double.toString(d);
        if (string.indexOf('.') > 0 && string.indexOf('e') < 0 && string.indexOf('E') < 0) {

            while (string.endsWith("0")) {

                string = string.substring(0, string.length() - 1);

            }

            if (string.endsWith(".")) {

                string = string.substring(0, string.length() - 1);

            }

        }

        return string;

    }

    // Get an array of field names from a JSONObject, or null if it has none.
    public static String[] getNames(JSONObject jo) {

        int length = jo.length();
        if (length == 0) {

            return null;

        }

        Iterator iterator = jo.keys();
        String[] names = new String[length];
        int i = 0;
        while (iterator.hasNext()) {

            names[i] = (String) iterator.next();
            i += 1;

        }

        return names;

    }

    // Get an array of public field names from an Object, or null if there are none.
    public static String[] getNames(Object object) {

        if (object == null) {

            return null;

        }

        Class klass = object.getClass();
        Field[] fields = klass.getFields();
        int length = fields.length;
        if (length == 0) {

            return null;

        }

        String[] names = new String[length];
        for (int i = 0; i < length; i += 1) {

            names[i] = fields[i].getName();

        }

        return names;

    }

    // Produce a string from a Number; throws if the number is null or not finite.
    public static String numberToString(Number number) throws JSONException {

        if (number == null) {

            throw new JSONException("Null pointer");

        }

        testValidity(number);

        // Shave off trailing zeros and decimal point, if possible.
        String string = number.toString();
        if (string.indexOf('.') > 0 && string.indexOf('e') < 0 && string.indexOf('E') < 0) {

            while (string.endsWith("0")) {

                string = string.substring(0, string.length() - 1);

            }

            if (string.endsWith(".")) {

                string = string.substring(0, string.length() - 1);

            }

        }

        return string;

    }

    // Produce a string in double quotes with backslash sequences in all the right
    // places, escaping "</" as "<\/" so JSON text can be delivered in HTML.
    public static String quote(String string) {

        StringWriter sw = new StringWriter();
        synchronized (sw.getBuffer()) {

            try {

                return quote(string, sw).toString();

            } catch (IOException ignored) {

                // will never happen - we are writing to a string writer
                return "";

            }

        }

    }

    public static Writer quote(String string, Writer w) throws IOException {

        if (string == null || string.length() == 0) {

            w.write("\"\"");
            return w;

        }

        char b;
        char c = 0;
        String hhhh;
        int i;
        int len = string.length();

        w.write('"');
        for (i = 0; i < len; i += 1) {

            b = c;
            c = string.charAt(i);
            switch (c) {

                case '\\':
                case '"':
                    w.write('\\');
                    w.write(c);
                    break;
                case '/':
                    if (b == '<') {

                        w.write('\\');

                    }
                    w.write(c);
                    break;
                case '\b':
                    w.write("\\b");
                    break;
                case '\t':
                    w.write("\\t");
                    break;
                case '\n':
                    w.write("\\n");
                    break;
                case '\f':
                    w.write("\\f");
                    break;
                case '\r':
                    w.write("\\r");
                    break;
                default:
                    if (c < ' ' || (c >= '\u0080' && c < '\u00a0') || (c >= '\u2000' && c < '\u2100')) {

                        w.write("\\u");
                        hhhh = Integer.toHexString(c);
                        w.write("0000", 0, 4 - hhhh.length());
                        w.write(hhhh);

                    } else {

                        w.write(c);

                    }

            }

        }

        w.write('"');
        return w;

    }

    // Try to convert a string into a number, boolean, or null.
    // If the string cannot be converted, return the string.
    public static Object stringToValue(String string) {

        Double d;
        if (string.equals("")) {

            return string;

        }

        if (string.equalsIgnoreCase("true")) {

            return Boolean.TRUE;

        }

        if (string.equalsIgnoreCase("false")) {

            return Boolean.FALSE;

        }

        if (string.equalsIgnoreCase("null")) {

            return JSONObject.NULL;

        }

        // If it might be a number, try converting it. If a number cannot be
        // produced, then the value will just be a string.
        char b = string.charAt(0);
        if ((b >= '0' && b <= '9') || b == '-') {

            try {

                if (string.indexOf('.') > -1 || string.indexOf('e') > -1 || string.indexOf('E') > -1) {

                    d = Double.valueOf(string);
                    if (!d.isInfinite() && !d.isNaN()) {

                        return d;

                    }

                } else {

                    Long myLong = new Long(string);
                    if (string.equals(myLong.toString())) {

                        if (myLong.longValue() == myLong.intValue()) {

                            return new Integer(myLong.intValue());

                        } else {

                            return myLong;

                        }

                    }

                }

            } catch (NumberFormatException ignore) {

            }

        }

        return string;

    }

    // Throw a JSONException if the object is a NaN or infinite number.
    public static void testValidity(Object o) throws JSONException {

        if (o != null) {

            if (o instanceof Double) {

                if (((Double) o).isInfinite() || ((Double) o).isNaN()) {

                    throw new JSONException("JSON does not allow non-finite numbers.");

                }

            } else if (o instanceof Float) {

                if (((Float) o).isInfinite() || ((Float) o).isNaN()) {

                    throw new JSONException("JSON does not allow non-finite numbers.");

                }

            }

        }

    }

    // Make a JSON text of an Object value, honoring a JSONString's toJSONString()
    // if present. Assumes the data structure is acyclical.
    public static String valueToString(Object value) throws JSONException {

        if (value == null || value.equals(null)) {

            return "null";

        }

        if (value instanceof JSONString) {

            Object object;
            try {

                object = ((JSONString) value).toJSONString();

            } catch (Exception e) {

                throw new JSONException(e);

            }

            if (object instanceof String) {

                return (String) object;

            }

            throw new JSONException("Bad value from toJSONString: " + object);

        }

        if (value instanceof Number) {

            return numberToString((Number) value);

        }

        if (value instanceof Boolean || value instanceof JSONObject || value instanceof JSONArray) {

            return value.toString();

        }

        if (value instanceof Map) {

            return new JSONObject((Map) value).toString();

        }

        if (value instanceof Collection) {

            return new JSONArray((Collection) value).toString();

        }

        if (value.getClass().isArray()) {

            return new JSONArray(value).toString();

        }

        return quote(value.toString());

    }

    // Wrap if necessary: NULL for null, JSONArray for arrays/collections,
    // JSONObject for maps and beans, strings for java types. Null on failure.
    public static Object wrap(Object object) {

        try {

            if (object == null) {

                return NULL;

            }

            if (object instanceof JSONObject || object instanceof JSONArray || NULL.equals(object)
                    || object instanceof JSONString || object instanceof Byte || object instanceof Character
                    || object instanceof Short || object instanceof Integer || object instanceof Long
                    || object instanceof Boolean || object instanceof Float || object instanceof Double
                    || object instanceof String)
            {

                return object;

            }

            if (object instanceof Collection) {

                return new JSONArray((Collection) object);

            }

            if (object.getClass().isArray()) {

                return new JSONArray(object);

            }

            if (object instanceof Map) {

                return new JSONObject((Map) object);

            }

            Package objectPackage = object.getClass().getPackage();
            String objectPackageName = objectPackage != null ? objectPackage.getName() : "";
            if (objectPackageName.startsWith("java.") || objectPackageName.startsWith("javax.")
                    || object.getClass().getClassLoader() == null)
            {

                return object.toString();

            }

            return new JSONObject(object);

        } catch (JSONException exception) {

            return null;

        }

    }

    static final Writer writeValue(Writer writer, Object value, int indentFactor, int indent)
            throws JSONException, IOException
    {

        if (value == null || value.equals(null)) {

            writer.write("null");

        } else if (value instanceof JSONObject) {

            ((JSONObject) value).write(writer, indentFactor, indent);

        } else if (value instanceof JSONArray) {

            ((JSONArray) value).write(writer, indentFactor, indent);

        } else if (value instanceof Map) {

            new JSONObject((Map) value).write(writer, indentFactor, indent);

        } else if (value instanceof Collection) {

            new JSONArray((Collection) value).write(writer, indentFactor, indent);

        } else if (value.getClass().isArray()) {

            new JSONArray(value).write(writer, indentFactor, indent);

        } else if (value instanceof Number) {

            writer.write(numberToString((Number) value));

        } else if (value instanceof Boolean) {

            writer.write(value.toString());

        } else if (value instanceof JSONString) {

            Object o;
            try {

                o = ((JSONString) value).toJSONString();

            } catch (Exception e) {

                throw new JSONException(e);

            }

            writer.write(o != null ? o.toString() : quote(value.toString()));

        } else {

            quote(value.toString(), writer);

        }

        return writer;

    }

    static final void indent(Writer writer, int indent) throws IOException {

        for (int i = 0; i < indent; i += 1) {

            writer.write(' ');

        }

    }

    // Accumulate values under a key: unlike put, an existing value is combined
    // with the new one in a JSONArray. Throws on invalid value or null key.
    public JSONObject accumulate(String key, Object value) throws JSONException {

        testValidity(value);
        Object object = opt(key);
        if (object == null) {

            put(key, value instanceof JSONArray ? new JSONArray().put(value) : value);

        } else if (object instanceof JSONArray) {

            ((JSONArray) object).put(value);

        } else {

            put(key, new JSONArray().put(object).put(value));

        }

        return this;

    }

    // Append a value to the JSONArray under a key, creating the array if the key is
    // absent. Throws if the key already holds a value that is not a JSONArray.
    public JSONObject append(String key, Object value) throws JSONException {

        testValidity(value);
        Object object = opt(key);
        if (object == null) {

            put(key, new JSONArray().put(value));

        } else if (object instanceof JSONArray) {

            put(key, ((JSONArray) object).put(value));

        } else {

            throw new JSONException("JSONObject[" + key + "] is not a JSONArray.");

        }

        return this;

    }

    // Get the value associated with a key; throws if the key is null or not found.
    public Object get(String key) throws JSONException {

        if (key == null) {

            throw new JSONException("Null key.");

        }

        Object object = opt(key);
        if (object == null) {

            throw new JSONException("JSONObject[" + quote(key) + "] not found.");

        }

        return object;

    }

    // Get the boolean value associated with a key. The strings "true" and "false"
    // (case insensitive) are converted; throws if not convertible to boolean.
    public boolean getBoolean(String key) throws JSONException {

        Object object = get(key);
        if (object.equals(Boolean.FALSE) || (object instanceof String && ((String) object).equalsIgnoreCase("false"))) {

            return false;

        } else if (object.equals(Boolean.TRUE)
                || (object instanceof String && ((String) object).equalsIgnoreCase("true")))
        {

            return true;

        }

        throw new JSONException("JSONObject[" + quote(key) + "] is not a Boolean.");

    }

    // Get the double value associated with a key; throws if it cannot be converted
    // to a number.
    public double getDouble(String key) throws JSONException {

        Object object = get(key);
        try {

            return object instanceof Number ? ((Number) object).doubleValue() : Double.parseDouble((String) object);

        } catch (NumberFormatException e) {

            throw new JSONException("JSONObject[" + quote(key) + "] is not a number.");

        }

    }

    // Get the int value associated with a key; throws if it cannot be converted to
    // an integer.
    public int getInt(String key) throws JSONException {

        Object object = get(key);
        try {

            return object instanceof Number ? ((Number) object).intValue() : Integer.parseInt((String) object);

        } catch (NumberFormatException e) {

            throw new JSONException("JSONObject[" + quote(key) + "] is not an int.");

        }

    }

    // Get the JSONArray associated with a key; throws if the value is not a
    // JSONArray.
    public JSONArray getJSONArray(String key) throws JSONException {

        Object object = get(key);
        if (object instanceof JSONArray) {

            return (JSONArray) object;

        }

        throw new JSONException("JSONObject[" + quote(key) + "] is not a JSONArray.");

    }

    // Get the JSONObject associated with a key; throws if the value is not a
    // JSONObject.
    public JSONObject getJSONObject(String key) throws JSONException {

        Object object = get(key);
        if (object instanceof JSONObject) {

            return (JSONObject) object;

        }

        throw new JSONException("JSONObject[" + quote(key) + "] is not a JSONObject.");

    }

    // Get the long value associated with a key; throws if it cannot be converted to
    // a long.
    public long getLong(String key) throws JSONException {

        Object object = get(key);
        try {

            return object instanceof Number ? ((Number) object).longValue() : Long.parseLong((String) object);

        } catch (NumberFormatException e) {

            throw new JSONException("JSONObject[" + quote(key) + "] is not a long.");

        }

    }

    // Get the string associated with a key; throws if there is no string value.
    public String getString(String key) throws JSONException {

        Object object = get(key);
        if (object instanceof String) {

            return (String) object;

        }

        throw new JSONException("JSONObject[" + quote(key) + "] not a string.");

    }

    public boolean has(String key) {

        return map.containsKey(key);

    }

    // Increment a numeric property, creating it with a value of 1 if absent.
    // Throws if an existing value is not an Integer, Long, Double, or Float.
    public JSONObject increment(String key) throws JSONException {

        Object value = opt(key);
        if (value == null) {

            put(key, 1);

        } else if (value instanceof Integer) {

            put(key, ((Integer) value).intValue() + 1);

        } else if (value instanceof Long) {

            put(key, ((Long) value).longValue() + 1);

        } else if (value instanceof Double) {

            put(key, ((Double) value).doubleValue() + 1);

        } else if (value instanceof Float) {

            put(key, ((Float) value).floatValue() + 1);

        } else {

            throw new JSONException("Unable to increment [" + quote(key) + "].");

        }

        return this;

    }

    // Returns true if there is no value for the key or the value is
    // JSONObject.NULL.
    public boolean isNull(String key) {

        return JSONObject.NULL.equals(opt(key));

    }

    public Iterator keys() {

        return keySet().iterator();

    }

    public Set keySet() {

        return map.keySet();

    }

    public int length() {

        return map.size();

    }

    // Produce a JSONArray of the key names, or null if the JSONObject is empty.
    public JSONArray names() {

        JSONArray ja = new JSONArray();
        Iterator keys = keys();
        while (keys.hasNext()) {

            ja.put(keys.next());

        }

        return ja.length() == 0 ? null : ja;

    }

    // Get an optional value associated with a key, or null if there is no value.
    public Object opt(String key) {

        return key == null ? null : map.get(key);

    }

    // Get an optional boolean; false if there is no such key or it is not
    // convertible.
    public boolean optBoolean(String key) {

        return optBoolean(key, false);

    }

    // Get an optional boolean; defaultValue if there is no such key or it is not
    // convertible.
    public boolean optBoolean(String key, boolean defaultValue) {

        try {

            return getBoolean(key);

        } catch (JSONException e) {

            return defaultValue;

        }

    }

    // Get an optional double; NaN if there is no such key or the value is not a
    // number.
    public double optDouble(String key) {

        return optDouble(key, Double.NaN);

    }

    // Get an optional double; defaultValue if there is no such key or the value is
    // not a number.
    public double optDouble(String key, double defaultValue) {

        try {

            return getDouble(key);

        } catch (JSONException e) {

            return defaultValue;

        }

    }

    // Get an optional int; zero if there is no such key or the value is not a
    // number.
    public int optInt(String key) {

        return optInt(key, 0);

    }

    // Get an optional int; defaultValue if there is no such key or the value is not
    // a number.
    public int optInt(String key, int defaultValue) {

        try {

            return getInt(key);

        } catch (JSONException e) {

            return defaultValue;

        }

    }

    // Get an optional JSONArray value, or null if there is none.
    public JSONArray optJSONArray(String key) {

        Object o = opt(key);
        return o instanceof JSONArray ? (JSONArray) o : null;

    }

    // Get an optional JSONObject value, or null if there is none.
    public JSONObject optJSONObject(String key) {

        Object object = opt(key);
        return object instanceof JSONObject ? (JSONObject) object : null;

    }

    // Get an optional long; zero if there is no such key or the value is not a
    // number.
    public long optLong(String key) {

        return optLong(key, 0);

    }

    // Get an optional long; defaultValue if there is no such key or the value is
    // not a number.
    public long optLong(String key, long defaultValue) {

        try {

            return getLong(key);

        } catch (JSONException e) {

            return defaultValue;

        }

    }

    // Get an optional string; empty string if there is no such key.
    // Non-null, non-string values are converted to a string.
    public String optString(String key) {

        return optString(key, "");

    }

    // Get an optional string, or defaultValue if there is no such key.
    public String optString(String key, String defaultValue) {

        Object object = opt(key);
        return NULL.equals(object) ? defaultValue : object.toString();

    }

    private void populateMap(Object bean) {

        Class klass = bean.getClass();

        // If klass is a System class then set includeSuperClass to false.
        boolean includeSuperClass = klass.getClassLoader() != null;

        Method[] methods = includeSuperClass ? klass.getMethods() : klass.getDeclaredMethods();
        for (int i = 0; i < methods.length; i += 1) {

            try {

                Method method = methods[i];
                if (Modifier.isPublic(method.getModifiers())) {

                    String name = method.getName();
                    String key = "";
                    if (name.startsWith("get")) {

                        if ("getClass".equals(name) || "getDeclaringClass".equals(name)) {

                            key = "";

                        } else {

                            key = name.substring(3);

                        }

                    } else if (name.startsWith("is")) {

                        key = name.substring(2);

                    }

                    if (key.length() > 0 && Character.isUpperCase(key.charAt(0))
                            && method.getParameterTypes().length == 0)
                    {

                        if (key.length() == 1) {

                            key = key.toLowerCase();

                        } else if (!Character.isUpperCase(key.charAt(1))) {

                            key = key.substring(0, 1).toLowerCase() + key.substring(1);

                        }

                        Object result = method.invoke(bean, (Object[]) null);
                        if (result != null) {

                            map.put(key, wrap(result));

                        }

                    }

                }

            } catch (Exception ignore) {

            }

        }

    }

    // Put a key/boolean pair in the JSONObject; throws if the key is null.
    public JSONObject put(String key, boolean value) throws JSONException {

        put(key, value ? Boolean.TRUE : Boolean.FALSE);
        return this;

    }

    // Put a key/value pair where the Collection value is wrapped as a JSONArray.
    public JSONObject put(String key, Collection value) throws JSONException {

        put(key, new JSONArray(value));
        return this;

    }

    // Put a key/double pair; throws if the key is null or the number is not finite.
    public JSONObject put(String key, double value) throws JSONException {

        put(key, new Double(value));
        return this;

    }

    // Put a key/int pair in the JSONObject; throws if the key is null.
    public JSONObject put(String key, int value) throws JSONException {

        put(key, new Integer(value));
        return this;

    }

    // Put a key/long pair in the JSONObject; throws if the key is null.
    public JSONObject put(String key, long value) throws JSONException {

        put(key, new Long(value));
        return this;

    }

    // Put a key/value pair where the Map value is wrapped as a JSONObject.
    public JSONObject put(String key, Map value) throws JSONException {

        put(key, new JSONObject(value));
        return this;

    }

    // Put a key/value pair in the JSONObject. A null value removes the key if it is
    // present. Throws if the key is null or the value is a non-finite number.
    public JSONObject put(String key, Object value) throws JSONException {

        String pooled;
        if (key == null) {

            throw new NullPointerException("Null key.");

        }

        if (value != null) {

            testValidity(value);
            pooled = (String) keyPool.get(key);
            if (pooled == null) {

                if (keyPool.size() >= keyPoolSize) {

                    keyPool = new HashMap(keyPoolSize);

                }

                keyPool.put(key, key);

            } else {

                key = pooled;

            }

            map.put(key, value);

        } else {

            remove(key);

        }

        return this;

    }

    // Put a key/value pair only if the key and value are both non-null;
    // throws if the key is a duplicate.
    public JSONObject putOnce(String key, Object value) throws JSONException {

        if (key != null && value != null) {

            if (opt(key) != null) {

                throw new JSONException("Duplicate key \"" + key + "\"");

            }

            put(key, value);

        }

        return this;

    }

    // Put a key/value pair only if the key and value are both non-null.
    public JSONObject putOpt(String key, Object value) throws JSONException {

        if (key != null && value != null) {

            put(key, value);

        }

        return this;

    }

    // Remove a name and its value, if present. Returns the removed value, or null.
    public Object remove(String key) {

        return map.remove(key);

    }

    // Make a compact JSON text of this JSONObject. Returns null if a syntactically
    // correct text cannot be produced. Assumes the data structure is acyclical.
    @Override
    public String toString() {

        try {

            return toString(0);

        } catch (JSONException e) {

            return null;

        }

    }

    // Produce a JSONArray containing the values for the given key names, in order.
    // Returns null if names is empty; throws on non-finite numbers.
    public JSONArray toJSONArray(JSONArray names) throws JSONException {

        if (names == null || names.length() == 0) {

            return null;

        }

        JSONArray ja = new JSONArray();
        for (int i = 0; i < names.length(); i += 1) {

            ja.put(opt(names.getString(i)));

        }

        return ja;

    }

    // Make a prettyprinted JSON text of this JSONObject. Assumes the data structure
    // is acyclical.
    public String toString(int indentFactor) throws JSONException {

        StringWriter w = new StringWriter();
        synchronized (w.getBuffer()) {

            return write(w, indentFactor, 0).toString();

        }

    }

    // Write the contents as compact JSON text to a writer. Assumes the data
    // structure is acyclical.
    public Writer write(Writer writer) throws JSONException {

        return write(writer, 0, 0);

    }

    // Write the contents as JSON text to a writer, with the given indentation.
    // Assumes the data structure is acyclical.
    Writer write(Writer writer, int indentFactor, int indent) throws JSONException {

        try {

            boolean commanate = false;
            int length = length();
            Iterator keys = keys();
            writer.write('{');

            if (length == 1) {

                Object key = keys.next();
                writer.write(quote(key.toString()));
                writer.write(':');
                if (indentFactor > 0) {

                    writer.write(' ');

                }

                writeValue(writer, map.get(key), indentFactor, indent);

            } else if (length != 0) {

                int newindent = indent + indentFactor;
                while (keys.hasNext()) {

                    Object key = keys.next();
                    if (commanate) {

                        writer.write(',');

                    }

                    if (indentFactor > 0) {

                        writer.write('\n');

                    }

                    indent(writer, newindent);
                    writer.write(quote(key.toString()));
                    writer.write(':');
                    if (indentFactor > 0) {

                        writer.write(' ');

                    }

                    writeValue(writer, map.get(key), indentFactor, newindent);
                    commanate = true;

                }

                if (indentFactor > 0) {

                    writer.write('\n');

                }

                indent(writer, indent);

            }

            writer.write('}');
            return writer;

        } catch (IOException exception) {

            throw new JSONException(exception);

        }

    }

    // JSONObject.NULL is equivalent to JavaScript's null, whilst Java's null is
    // equivalent to JavaScript's undefined.
    private static final class Null {

        // A Null object is equal to the null value and to itself.
        @Override
        public boolean equals(Object object) {

            return object == null || object == this;

        }

        // NULL is intended to be a singleton, so clone returns this.
        @Override
        protected final Object clone() {

            return this;

        }

        @Override
        public String toString() {

            return "null";

        }

    }

}
