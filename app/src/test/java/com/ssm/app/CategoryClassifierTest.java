package com.ssm.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CategoryClassifierTest {
    @Test
    public void convenienceStoreIsFood() {
        assertEquals("식비", CategoryClassifier.classify("씨유(CU) 범천점", ""));
    }

    @Test
    public void merchantSimilarityNormalizesPunctuation() {
        assertTrue(MerchantNormalizer.similar("맘스터치&피자 부산", "맘스터치 피자 부산"));
    }
}
