package me.eccentric_nz.gamemodeinventories.JSON;

// Lets a class supply its own JSON serialization: toJSONString() is used by JSONObject.toString(),
// JSONArray.toString(), and JSONWriter.value(Object) instead of quoting the Object's toString().
public interface JSONString {

    // Returns a strictly syntactically correct JSON text representing this object.
    public String toJSONString();

}
