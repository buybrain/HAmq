package io.buybrain.hamq;

import io.buybrain.util.Env;
import lombok.val;
import org.testng.annotations.Test;

import java.util.HashMap;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class ConfigTest {
    @Test
    public void testFromEnv() {
        val env = new Env(new HashMap<String, String>(){{
            put("MQ_HOST", "a");
            put("MQ_PORT", "123");
            put("MQ_USER", "b");
            put("MQ_PASS", "c");
            put("MQ_VHOST", "d");
        }});
        
        val config = Config.ofEnv(env, "MQ_");
        val expected = new Config().withHost("a").withPort(123).withUsername("b").withPassword("c").withVhost("d");
        
        assertThat(config, is(expected));
    }
}
