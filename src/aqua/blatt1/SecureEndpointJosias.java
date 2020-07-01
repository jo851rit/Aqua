package aqua.blatt1;

import aqua.blatt1.common.msgtypes.KeyExchangeMessage;
import messaging.Endpoint;
import messaging.Message;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.InetSocketAddress;
import java.security.*;
import java.util.HashMap;
import java.util.Map;

public class SecureEndpointJosias extends Endpoint {

    //SecretKeySpec key;
    Map<InetSocketAddress, PublicKey> publicKeysOfOtherClients = new HashMap<>();

    PrivateKey privateKey;
    PublicKey publicKey;

    Cipher decrypt;

    public SecureEndpointJosias(int port) {
        super(port);
        init();
    }

    public SecureEndpointJosias() {
        super();
        init();
    }

    private void init() {
        //key = createSymKey();

        try {
            /*encrypt = Cipher.getInstance("AES");
            decrypt = Cipher.getInstance("AES");
            encrypt.init(Cipher.ENCRYPT_MODE, key);
            decrypt.init(Cipher.DECRYPT_MODE, key);*/

            KeyPair keyPair = generateAsyncKeypair();
            privateKey = keyPair.getPrivate();
            publicKey = keyPair.getPublic();

            decrypt = Cipher.getInstance("RSA");
            decrypt.init(Cipher.DECRYPT_MODE, privateKey);

        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void send(InetSocketAddress receiver, Serializable payload) {
        /*try {
            super.send(receiver, encrypt.doFinal(objToByte(payload)));
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            e.printStackTrace();
        }*/

        if (!publicKeysOfOtherClients.containsKey(receiver)) {
            super.send(receiver, new KeyExchangeMessage(publicKey));
        }
        while (!publicKeysOfOtherClients.containsKey(receiver)){
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        };

        try {
            Cipher encrypt = Cipher.getInstance("RSA");
            encrypt.init(Cipher.ENCRYPT_MODE, publicKeysOfOtherClients.get(receiver));
            super.send(receiver, encrypt.doFinal(objToByte(payload)));
        } catch (InvalidKeyException | BadPaddingException | IllegalBlockSizeException | NoSuchPaddingException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Message blockingReceive() {
        /*Message message = null;
        try {
            Message receivedMessage = super.blockingReceive();
            Serializable decodedPayload = (Serializable) byteToObj(decrypt.doFinal((byte[]) receivedMessage.getPayload()));
            message = new Message(decodedPayload, receivedMessage.getSender());
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            e.printStackTrace();
        }
        return message;*/
        Message message = super.blockingReceive();
        if (message.getPayload() instanceof KeyExchangeMessage
                && !publicKeysOfOtherClients.containsKey(message.getSender())) {
            KeyExchangeMessage keyExchangeMessage = (KeyExchangeMessage) message.getPayload();
            publicKeysOfOtherClients.put(message.getSender(), keyExchangeMessage.getPublicKey());
            super.send(message.getSender(), new KeyExchangeMessage(publicKey));
            return super.blockingReceive();
        } else {
            Serializable decodedPayload;
            try {
                decodedPayload = (Serializable) byteToObj(decrypt.doFinal((byte[]) message.getPayload()));
                return new Message(decodedPayload, message.getSender());
            } catch (IllegalBlockSizeException | BadPaddingException e) {
                e.printStackTrace();
                return null;
            }
        }

    }

    @Override
    public Message nonBlockingReceive() {
        Message message = null;
        try {
            Message receivedMessage = super.nonBlockingReceive();
            Serializable decodedPayload = decrypt.doFinal(objToByte(receivedMessage.getPayload()));
            message = new Message(decodedPayload, receivedMessage.getSender());
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            e.printStackTrace();
        }
        return message;
    }

    private SecretKeySpec createSymKey() {
        return new SecretKeySpec("CAFEBABECAFEBABE".getBytes(), "AES");
    }

    private KeyPair generateAsyncKeypair() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(5000);
        return generator.generateKeyPair();
    }

    private byte[] objToByte(Object obj) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ObjectOutputStream outputStream;
        byte[] objBytes = null;

        try {
            outputStream = new ObjectOutputStream(byteArrayOutputStream);
            outputStream.writeObject(obj);
            outputStream.flush();
            objBytes = byteArrayOutputStream.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                byteArrayOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return objBytes;
    }

    private Object byteToObj(byte[] bytes) {
        ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
        ObjectInput in = null;
        Object o = null;
        try {
            in = new ObjectInputStream(bis);
            o = in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return o;
    }
}
