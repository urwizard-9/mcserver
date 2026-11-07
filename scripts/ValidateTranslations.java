import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Run with the pinned server's SnakeYAML on the classpath. No server mutation. */
class ValidateTranslations {
    static final Pattern TOKEN = Pattern.compile("\\{[^}]+}");
    static Map<String,String> flatten(Object node) {
        Map<String,String> result = new TreeMap<>(); walk(node, "", result); return result;
    }
    static void walk(Object node, String path, Map<String,String> result) {
        if (node instanceof Map<?,?> map) map.forEach((k,v) -> walk(v, path.isEmpty() ? k.toString() : path+"."+k, result));
        else if (node instanceof List<?> list) {
            for (int i=0;i<list.size();i++) walk(list.get(i), path+"["+i+"]", result);
        } else if (node instanceof String text) result.put(path,text);
        else throw new IllegalStateException("Non-string language value: " + path);
    }
    static List<String> tokens(String text) { return TOKEN.matcher(text).results().map(MatchResult::group).sorted().toList(); }
    static Map<String,String> read(String path) throws Exception {
        LoaderOptions options = new LoaderOptions(); options.setAllowDuplicateKeys(false);
        return flatten(new Yaml(new SafeConstructor(options)).load(Files.readString(Path.of(path))));
    }
    public static void main(String[] args) throws Exception {
        var en=read("config/lgmc/lang/en-reference.yml"); var ko=read("config/lgmc/lang/ko.yml");
        if (!en.keySet().equals(ko.keySet())) throw new IllegalStateException("Translation keys differ");
        for (var key: en.keySet()) {
            if (!tokens(en.get(key)).equals(tokens(ko.get(key)))) throw new IllegalStateException("Placeholder mismatch: "+key);
            if (ko.get(key).isBlank()) throw new IllegalStateException("Blank value: "+key);
            for (var token:tokens(ko.get(key))) {
                String ref=token.substring(1,token.length()-1);
                if (ref.contains(".") && !ko.containsKey(ref)) throw new IllegalStateException("Missing reference: "+ref);
            }
        }
        var messages=read("lobby/src/main/resources/messages.yml");
        String source=Files.readString(Path.of("lobby/src/main/java/local/lgmc/LobbyPlugin.java"));
        source += Files.readString(Path.of("lobby/src/main/java/local/lgmc/PreviewMenu.java"));
        var samples=Pattern.compile("sample\\(\"([^\"]+)\"").matcher(source);
        while(samples.find()) if(!ko.containsKey(samples.group(1)) && !samples.group(1).endsWith(".")) throw new IllegalStateException("Missing preview translation: "+samples.group(1));
        var calls=Pattern.compile("msg\\(\"([^\"]+)\"").matcher(source);
        while(calls.find()) if(!messages.containsKey(calls.group(1))) throw new IllegalStateException("Missing lobby message: "+calls.group(1));
        for(String key:List.of("ready-announcement","cancel-announcement")) {
            if(!tokens(messages.get(key)).equals(List.of("{online}","{player}","{ready}"))) throw new IllegalStateException(key);
        }
        System.out.println("PASS: " + ko.size() + " LGMC strings/list entries, identical keys/placeholders, internal references; " + messages.size() + " lobby messages");
    }
}
