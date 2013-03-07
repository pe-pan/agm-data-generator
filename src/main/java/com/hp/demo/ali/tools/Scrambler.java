// copied from hudson.util.Scrambler
/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.hp.demo.ali.tools;

import com.trilead.ssh2.crypto.Base64;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

/**
 * Used when storing passwords in configuration files.
 *
 * <p>
 * This doesn't make passwords secure, but it prevents unwanted
 * exposure to passwords, such as when one is grepping the file system
 * or looking at config files for trouble-shooting.
 *
 * @author Kohsuke Kawaguchi
 * @see Protector
 */
public class Scrambler {
    public static String scramble(String secret) {
        if(secret==null)    return null;
        try {
            return new String(Base64.encode(secret.getBytes("UTF-8")));
        } catch (UnsupportedEncodingException e) {
            throw new Error(e); // impossible
        }
    }

    public static String descramble(String scrambled) {
        if(scrambled==null)    return null;
        try {
            return new String(Base64.decode(scrambled.toCharArray()),"UTF-8");
        } catch (IOException e) {
            return "";  // corrupted data.
        }
    }
}
