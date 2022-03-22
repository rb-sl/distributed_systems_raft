package it.polimi.server.state;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

class StateTest {
    @Test
    public void restoreVarsTest() throws IOException{
        Gson gson = new Gson();
        Map<String, Integer> map = new HashMap<>();
        map.put("ciao", 1);


        String json = gson.toJson(map);
        System.out.println(json);

        Path path = Paths.get("./storage.json");
        byte[] strToBytes = json.getBytes();

        Files.write(path, strToBytes);

        map.put("hey", 2);
        json = gson.toJson(map);
        strToBytes = json.getBytes();
        Files.write(path, strToBytes);
    }
}