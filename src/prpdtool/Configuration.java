/*
 * Do what you want with this file
 */
package prpdtool;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


/** Reads/writes value from/to config file which consists of pairs <label>=<value>
 * 
 * @author jstar
 */
public class Configuration {
    private String config_file;
    private Map<String,String> config = new HashMap<>();
    
    public Configuration( String config_file ) {
        this.config_file = config_file;
    }
    
    private void readConfigFile() {
        config.clear();
        File configFile = new File(config_file);
        if (configFile.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(configFile))) {
                String line;
                while((line= br.readLine()) != null ) {
                    String [] w = line.trim().split("=");
                    if( w.length == 2 )
                        config.put(w[0], w[1]);
                }
            } catch (IOException e) {
            }
        }
    }
    
    private void writeConfigFile() throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(config_file))) {
            for( String k : config.keySet() )
                bw.write( k + "=" + config.get(k) + "\n");
        } 
    }
    
    // Helper - given label retrieves the value
    public String getValue( String label ) {
        readConfigFile();
        return config.get(label);
    }

    // Helper - saves the last used directory
    public void saveValue(String label, String value) throws IOException {
        readConfigFile();
        config.put( label, value);
        writeConfigFile();
    }
}
