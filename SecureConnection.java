/*
    Copyright 2017 Gregory Conrad

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
/**
 * @author Gregory Conrad
 * @date March 2017
 * @description An easy way to create a secure connection between two parties
 */

import java.security.AlgorithmParameterGenerator;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Properties;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.interfaces.DHPublicKey;
import javax.crypto.spec.DHParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.IvParameterSpec;

import org.apache.commons.crypto.cipher.CryptoCipher;
import org.apache.commons.crypto.cipher.CryptoCipherFactory;
import org.apache.commons.crypto.cipher.CryptoCipherFactory.CipherProvider;
import org.apache.commons.crypto.utils.Utils;

public class SecureConnection {
    /*
     * Public Methods
     */
    //Encrypts a message with all of the keys
    public byte[] encrypt(byte[] bytes) throws Exception {
        for (String currKey : this.dhKeys) {
            bytes = AES.encrypt(bytes, currKey.getBytes("ISO-8859-1"));
        }
        return bytes;
    }

    //Decrypts a message with all of the keys
    public byte[] decrypt(byte[] bytes) throws Exception {
        for (int i = this.dhKeys.size() - 1; i >= 0; --i) {
            bytes = AES.decrypt(bytes, this.dhKeys.get(i).getBytes("ISO-8859-1"));
        }
        return bytes;
    }

    /*
     * AES Encryption
     */
    private static class AES {
        //The type of encryption to use
        final static String type = "AES/CBC/PKCS5Padding";

        //Encrypts data using AES
        static byte[] encrypt(byte[] data, byte[] key) throws Exception {
            Properties properties = new Properties();
            properties.setProperty(CryptoCipherFactory.CLASSES_KEY,
                    CipherProvider.JCE.getClassName());
            CryptoCipher cipher = Utils.getCipherInstance(type, properties);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new IvParameterSpec(key));
            byte[] encoded = new byte[data.length + 2000];
            int lengthOfEncoded = cipher.doFinal(data, 0, data.length, encoded, 0);
            cipher.close();
            return shorten(encoded, lengthOfEncoded);
        }

        //Decrypts data using AES
        static byte[] decrypt(byte[] data, byte[] key) throws Exception {
            Properties properties = new Properties();
            properties.setProperty(CryptoCipherFactory.CLASSES_KEY,
                    CipherProvider.JCE.getClassName());
            CryptoCipher cipher = Utils.getCipherInstance(type, properties);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new IvParameterSpec(key));
            byte[] decoded = new byte[data.length + 2000];
            int lengthOfDecoded = cipher.doFinal(data, 0, data.length, decoded, 0);
            cipher.close();
            return shorten(decoded, lengthOfDecoded);
        }
    }

    //Shortens a byte array down to the specified length
    private static byte[] shorten(byte[] data, int lengthOfData) {
        byte[] returnVal = new byte[lengthOfData];
        System.arraycopy(data, 0, returnVal, 0, returnVal.length);
        return returnVal;
    }

    /*
     * Key Management
     *
     * Some of the following code was adopted from:
     *   http://docs.oracle.com/javase/7/docs/technotes/guides/security/crypto/CryptoSpec.html#DH2Ex
     * Oracle and/or its affiliates own the rights to what they created of the following.
     *
     * Copyright (c) 1997, 2001, Oracle and/or its affiliates. All rights reserved.
     *
     * Redistribution and use in source and binary forms, with or without
     * modification, are permitted provided that the following conditions
     * are met:
     *
     *   - Redistributions of source code must retain the above copyright
     *     notice, this list of conditions and the following disclaimer.
     *
     *   - Redistributions in binary form must reproduce the above copyright
     *     notice, this list of conditions and the following disclaimer in the
     *     documentation and/or other materials provided with the distribution.
     *
     *   - Neither the name of Oracle nor the names of its
     *     contributors may be used to endorse or promote products derived
     *     from this software without specific prior written permission.
     *
     * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
     * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
     * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
     * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
     * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
     * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
     * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
     * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
     * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
     * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
     * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
     */
    private ArrayList<String> dhKeys = new ArrayList<String>();
    private KeyAgreement keyAgree;

    public byte[] getPublicKey() throws Exception {
        return this.getPublicKey(1024);
    }

    public byte[] getPublicKey(int size) throws Exception {
        AlgorithmParameterGenerator paramGen = AlgorithmParameterGenerator.getInstance("DH");
        paramGen.init(size);
        AlgorithmParameters params = paramGen.generateParameters();
        DHParameterSpec dhParamSpec = (DHParameterSpec) params.getParameterSpec(DHParameterSpec.class);
        return this.getPublicKeyStep2(dhParamSpec);
    }

    public byte[] getPublicKey(byte[] otherPubKeyBytes) throws Exception {
        KeyFactory keyFac = KeyFactory.getInstance("DH");
        X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(otherPubKeyBytes);
        PublicKey otherPubKey = keyFac.generatePublic(x509KeySpec);
        DHParameterSpec dhParamSpec = ((DHPublicKey) otherPubKey).getParams();
        return this.getPublicKeyStep2(dhParamSpec);
    }

    private byte[] getPublicKeyStep2(DHParameterSpec dhParamSpec) throws Exception {
        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("DH");
        keyPairGen.initialize(dhParamSpec);
        KeyPair keyPair = keyPairGen.generateKeyPair();
        this.keyAgree = KeyAgreement.getInstance("DH");
        this.keyAgree.init(keyPair.getPrivate());
        return keyPair.getPublic().getEncoded();
    }

    private byte[] generateSecret(byte[] otherPubKeyBytes) throws Exception {
        KeyFactory keyFac = KeyFactory.getInstance("DH");
        X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(otherPubKeyBytes);
        PublicKey otherPubKey = keyFac.generatePublic(x509KeySpec);
        this.keyAgree.doPhase(otherPubKey, true);
        return keyAgree.generateSecret();
    }

    //Called when the other end's public key is received
    public void processOtherPubKey(byte[] otherPubKeyBytes) throws Exception {
        this.dhKeys.add(new String(shorten(this.generateSecret(otherPubKeyBytes), 16), "ISO-8859-1"));
    }
}