package aqua.blatt1;

import aqua.blatt1.common.msgtypes.KeyExchangeMessage;
import messaging.Endpoint;
import messaging.Message;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.*;
import java.net.*;
import java.security.*;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SecureEndpoint extends Endpoint {
    KeyPairGenerator keyPairGenerator;
    PrivateKey privateKey;
    PublicKey publicKey;
    Cipher cipherRsaEncrypt = Cipher.getInstance("RSA");
    Cipher cipherRsaDecrypt = Cipher.getInstance("RSA");
    private final DatagramSocket socket;
    ExecutorService executor;
    Timer timer = new Timer();
    HashMap<InetSocketAddress, PublicKey> publicKeyMap = new HashMap<InetSocketAddress, PublicKey>();
    int NUMTHREADS = 5;


    public SecureEndpoint() throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException {
        keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair key = keyPairGenerator.generateKeyPair();
        this.publicKey = key.getPublic();
        this.privateKey = key.getPrivate();
        cipherRsaDecrypt.init(Cipher.DECRYPT_MODE, privateKey);
        executor = Executors.newFixedThreadPool(NUMTHREADS);
        try {
            this.socket = new DatagramSocket();
        } catch (SocketException var2) {
            throw new RuntimeException(var2);
        }
    }

    public SecureEndpoint(int port) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException {
        keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair key = keyPairGenerator.generateKeyPair();
        this.publicKey = key.getPublic();
        this.privateKey = key.getPrivate();
        cipherRsaDecrypt.init(Cipher.DECRYPT_MODE, privateKey);
        executor = Executors.newFixedThreadPool(NUMTHREADS);
        try {
            this.socket = new DatagramSocket(port);
        } catch (SocketException var2) {
            throw new RuntimeException(var2);
        }
    }

    @Override
    public void send(InetSocketAddress receiver, Serializable payload) {
        if (!publicKeyMap.containsKey(receiver)) {
            keyExchangeMethod(receiver/*, true*/);
            executor.execute(() -> {
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        if (publicKeyMap.containsKey(receiver)) {
                            System.out.println("Receiver Send in Thread: " + receiver);
                            executeSend(receiver, payload);
                            timer.cancel();
                        }
                    }
                }, 0, 500);
            });
        } else {
            executeSend(receiver, payload);
        }
    }

    public void executeSend(InetSocketAddress receiver, Serializable payload) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(payload);

            cipherRsaEncrypt.init(Cipher.ENCRYPT_MODE, publicKeyMap.get(receiver));

            byte[] bytes = baos.toByteArray();
            byte[] cipherText = cipherRsaEncrypt.doFinal(bytes);

            DatagramPacket datagram = new DatagramPacket(cipherText, cipherText.length, receiver);
            this.socket.send(datagram);
        } catch (Exception var7) {
            throw new RuntimeException(var7);
        }
    }

    public void keyExchangeMethod(InetSocketAddress receiver/*, boolean tellMeYours*/) {
        try {
            KeyExchangeMessage keyExchangeMessage = new KeyExchangeMessage(publicKey/*, tellMeYours*/);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(keyExchangeMessage);
            byte[] bytes = baos.toByteArray();

            DatagramPacket datagram = new DatagramPacket(bytes, bytes.length, receiver);
            this.socket.send(datagram);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Message blockingReceive() {
        DatagramPacket datagram = new DatagramPacket(new byte[1024], 1024);

        try {
            this.socket.receive(datagram);
        } catch (Exception var3) {
            throw new RuntimeException(var3);
        }

        try {
            byte[] original = cipherRsaDecrypt.doFinal(datagram.getData());

            datagram.setData(original);
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            datagram.setData(datagram.getData());
//            e.printStackTrace();
        }

        Message message = readDatagram(datagram);
        try {
            if (message.getPayload() instanceof KeyExchangeMessage && !publicKeyMap.containsKey(message.getSender())) {
                publicKeyMap.put(message.getSender(), ((KeyExchangeMessage) message.getPayload()).getPublicKey());

                keyExchangeMethod(message.getSender());
            }
        } catch (Exception var7) {
            throw new RuntimeException(var7);
        }
        return message;
    }

    private Message readDatagram(DatagramPacket datagram) {
        try {
            ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(datagram.getData()));
            return new Message((Serializable) ois.readObject(), (InetSocketAddress) datagram.getSocketAddress());
        } catch (Exception var3) {
            throw new RuntimeException(var3);
        }
    }

//    @Override
//    public Message nonBlockingReceive() {
//        DatagramPacket datagram = new DatagramPacket(new byte[1024], 1024);
//        System.out.println("Test");
//        try {
//            this.socket.setSoTimeout(1);
//        } catch (SocketException var7) {
//            throw new RuntimeException(var7);
//        }
//
//        boolean timeoutExpired;
//        try {
//            this.socket.receive(datagram);
//            timeoutExpired = false;
//        } catch (SocketTimeoutException var5) {
//            timeoutExpired = true;
//        } catch (IOException var6) {
//            throw new RuntimeException(var6);
//        }
//
//        try {
//            this.socket.setSoTimeout(0);
//        } catch (SocketException var4) {
//            throw new RuntimeException(var4);
//        }
//
//        try {
//            byte[] original = cipherRsaDecrypt.doFinal(datagram.getData());
//            datagram.setData(original);
//        } catch (IllegalBlockSizeException | BadPaddingException e) {
//            e.printStackTrace();
//        }
//
//        return timeoutExpired ? null : this.readDatagram(datagram);
//    }


}
