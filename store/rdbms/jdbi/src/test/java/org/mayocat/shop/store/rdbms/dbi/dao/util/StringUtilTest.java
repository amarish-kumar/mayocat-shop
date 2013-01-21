package org.mayocat.shop.store.rdbms.dbi.dao.util;

import org.junit.Test;

import junit.framework.Assert;

/**
 * @version $Id$
 */
public class StringUtilTest
{
    @Test
    public void testSnakify()
    {
        Assert.assertEquals("foo", StringUtil.snakify("foo"));
        Assert.assertEquals("its_fun_to_stay_at_the_ymca", StringUtil.snakify("itsFunToStayAtTheYMCA"));
    }
}