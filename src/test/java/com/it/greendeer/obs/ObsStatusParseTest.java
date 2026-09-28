package com.it.greendeer.obs;

import com.it.greendeer.common.Json;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * OBS v5 RequestResponse 状态解析测试（requestStatus.result 语义）。
 */
class ObsStatusParseTest {

    @Test
    void v5SuccessIsNull() {
        String json = """
                {"requestType":"GetSceneList","requestId":"1",
                 "requestStatus":{"result":true,"code":100},
                 "responseData":{"currentProgramSceneName":"Scene1"}}
                """;
        assertNull(ObsClient.requestError(Json.parse(json)));
    }

    @Test
    void v5FailureCarriesCodeAndComment() {
        String json = """
                {"requestType":"GetSceneList","requestId":"1",
                 "requestStatus":{"result":false,"code":600,"comment":"Scene list is empty"},
                 "responseData":{}}
                """;
        String err = ObsClient.requestError(Json.parse(json));
        assertNotNull(err);
        assertEquals("code=600, Scene list is empty", err);
    }

    @Test
    void legacyV4StyleIsStillSupported() {
        String ok = "{\"status\":\"ok\",\"responseData\":{}}";
        assertNull(ObsClient.requestError(Json.parse(ok)));

        String bad = "{\"status\":\"error\",\"comment\":\"not implemented\",\"responseData\":{}}";
        assertEquals("not implemented", ObsClient.requestError(Json.parse(bad)));
    }
}
