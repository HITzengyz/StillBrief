package com.focusinbox.app

import org.junit.Assert.*
import org.junit.Test

class VerificationCodesTest {
    @Test fun extractsExplicitChineseAndEnglishCodes() {
        listOf(
            "【银行】您的验证码为：123456，5分钟内有效" to "123456",
            "验证码 0321，请勿泄露" to "0321",
            "Your verification code is 654321. Expires in 10 minutes." to "654321",
            "823451 is your security code." to "823451",
            "OTP: A1B2C3" to "A1B2C3",
            "【验证码】123456" to "123456",
            "Your code is: 123456" to "123456",
            "G-123456 is your Google verification code." to "123456",
            "Use 123456 as Microsoft account security code." to "123456"
        ).forEach { (text,expected) -> assertEquals(text,expected,VerificationCodes.extract("",text)) }
        assertEquals("654321",VerificationCodes.extract("验证码","654321"))
    }
    @Test fun ordinaryNumbersHiddenContentAndAmbiguousCodesDoNotMatch() {
        listOf("订单123456已发货", "未接来电13812345678", "验证码将在5分钟后失效", "验证码：13812345678",
            "Your verification code is ready", "验证码123456\n验证码654321", "请输入验证码", "优惠码SAVE20").forEach {
            assertNull(it,VerificationCodes.extract("",it))
        }
    }
    @Test fun collapsedAndExpandedCopiesResolveToOneCode() {
        assertEquals("123456",VerificationCodes.extract("验证码123456","验证码123456，5分钟内有效"))
        assertEquals(VerificationCodes.key("sms","123456"),VerificationCodes.key("sms","123456"))
        assertNotEquals(VerificationCodes.key("sms","123456"),VerificationCodes.key("mail","123456"))
    }
    @Test fun onlyEnabledFeishuAndFreshCodesQualify() {
        assertTrue(VerificationCodes.active(true,DeliveryTarget.FEISHU))
        assertFalse(VerificationCodes.active(false,DeliveryTarget.FEISHU))
        assertFalse(VerificationCodes.active(true,DeliveryTarget.LOCAL))
        assertTrue(VerificationCodes.fresh(1000,1000))
        assertFalse(VerificationCodes.fresh(1000,601000))
    }
}
