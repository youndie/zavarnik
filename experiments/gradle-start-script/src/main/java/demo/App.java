package demo;
import com.google.gson.Gson;
import java.util.*;
public class App { public static void main(String[] a) throws Exception {
  Map<String,Object> m = new LinkedHashMap<>(); for (int i=0;i<500;i++) m.put("k"+i, List.of(i, "v"+i));
  System.out.println(new Gson().toJson(m).length());
}}
