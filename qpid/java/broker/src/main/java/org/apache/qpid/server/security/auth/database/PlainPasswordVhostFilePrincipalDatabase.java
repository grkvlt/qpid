/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.    
 *
 * 
 */
package org.apache.qpid.server.security.auth.database;

import org.apache.qpid.server.security.auth.database.PrincipalDatabase;
import org.apache.qpid.server.security.auth.sasl.AuthenticationProviderInitialiser;
import org.apache.qpid.server.security.auth.sasl.crammd5.CRAMMD5Initialiser;
import org.apache.qpid.server.security.auth.sasl.plain.PlainInitialiser;
import org.apache.qpid.server.security.access.AccessManager;
import org.apache.qpid.server.security.access.AccessResult;
import org.apache.qpid.server.security.access.Accessable;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.log4j.Logger;

import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.login.AccountNotFoundException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.regex.Pattern;
import java.util.Map;
import java.util.HashMap;
import java.security.Principal;

/**
 * Represents a user database where the account information is stored in a simple flat file.
 *
 * The file is expected to be in the form: username:password username1:password1 ... usernamen:passwordn
 *
 * where a carriage return separates each username/password pair. Passwords are assumed to be in plain text.
 */
public class PlainPasswordVhostFilePrincipalDatabase extends PlainPasswordFilePrincipalDatabase implements AccessManager
{
    private static final Logger _logger = Logger.getLogger(PlainPasswordVhostFilePrincipalDatabase.class);

    /**
     * Looks up the virtual hosts for a specified user in the password file.
     *
     * @param user The user to lookup
     *
     * @return a list of virtualhosts
     */
    private String[] lookupVirtualHost(String user)
    {
        try
        {
            BufferedReader reader = null;
            try
            {
                reader = new BufferedReader(new FileReader(_passwordFile));
                String line;

                while ((line = reader.readLine()) != null)
                {
                    String[] result = _regexp.split(line);
                    if (result == null || result.length < 3)
                    {
                        continue;
                    }

                    if (user.equals(result[0]))
                    {
                        return result[2].split(",");
                    }
                }
                return null;
            }
            finally
            {
                if (reader != null)
                {
                    reader.close();
                }
            }
        }
        catch (IOException ioe)
        {
            //ignore
        }
        return null;
    }


    public AccessResult isAuthorized(Accessable accessObject, String username)
    {
        if (accessObject instanceof VirtualHost)
        {
            String[] hosts = lookupVirtualHost(username);

            if (hosts != null)
            {
                for (String host : hosts)
                {
                    if (accessObject.getAccessableName().equals(host))
                    {
                        return new AccessResult(this, AccessResult.AccessStatus.GRANTED);
                    }
                }
            }
        }

        return new AccessResult(this, AccessResult.AccessStatus.REFUSED);
    }

    public String getName()
    {
        return "PlainPasswordVhostFile";
    }
}
