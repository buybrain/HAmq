package io.buybrain.hamq;

import org.testng.annotations.Test;

public class ConfigTest {
    @Test
    public void testConfig() {
        new Config().withHost("myhost").withPort(1234).withUsername("a").withPassword("b").withVhost("test");
    }
}
