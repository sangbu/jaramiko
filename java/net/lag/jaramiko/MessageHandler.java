/*
 * Copyright (C) 2005 Robey Pointer <robey@lag.net>
 *
 * This file is part of paramiko.
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * 
 * 
 * Created on May 30, 2005
 */

package net.lag.jaramiko;

import java.io.IOException;

/**
 * Handler for specific message types in the SSH2 protocol.  This is used to
 * split up handling into sub-components of similar functionality, instead of
 * having one huge Transport class.
 * 
 * @author robey
 */
/* package */ interface MessageHandler
{
    /**
     * Handle an SSH protocol message and return true if the message was
     * understood, or false if it wasn't (and wasn't handled).  This is part
     * of paramiko's internal implementation.
     * 
     * @param ptype message type
     * @param m message
     * @return true if the message was handled; false otherwise
     * @throws IOException if an exception occurred
     */
    public boolean handleMessage (byte ptype, Message m) throws IOException;
}