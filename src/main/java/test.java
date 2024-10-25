import java.util.concurrent.ConcurrentHashMap;

public class test {

    public static void main(String[] args) {
        var map = new ConcurrentHashMap<String, String>();
        var key = "some-key";
        var value = map.computeIfAbsent(key, k -> k);
    }
}
