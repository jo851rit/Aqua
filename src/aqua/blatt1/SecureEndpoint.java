package aqua.blatt1;

import messaging.Endpoint;
import messaging.Message;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.*;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class SecureEndpoint extends Endpoint {
    final String startString = "CAFEBABECAFEBABE";
    SecretKeySpec secretKeySpec;
    Cipher cipherAesEncrypt = Cipher.getInstance("AES");
    Cipher cipherAesDecrypt = Cipher.getInstance("AES/ECB/NoPadding");
    private final DatagramSocket socket;


    public SecureEndpoint() throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException {
        this.secretKeySpec = new SecretKeySpec(startString.getBytes(), "AES");
        this.cipherAesEncrypt.init(Cipher.ENCRYPT_MODE, secretKeySpec);
        this.cipherAesDecrypt.init(Cipher.DECRYPT_MODE, secretKeySpec);
        try {
            this.socket = new DatagramSocket();
        } catch (SocketException var2) {
            throw new RuntimeException(var2);
        }
    }

    public SecureEndpoint(int port) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException {
//        super(port);
        this.secretKeySpec = new SecretKeySpec(startString.getBytes(), "AES");
        this.cipherAesEncrypt.init(Cipher.ENCRYPT_MODE, secretKeySpec);
        this.cipherAesDecrypt.init(Cipher.DECRYPT_MODE, secretKeySpec);
        try {
            this.socket = new DatagramSocket(port);
        } catch (SocketException var2) {
            throw new RuntimeException(var2);
        }
    }

    @Override
    public void send(InetSocketAddress receiver, Serializable payload) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(payload);

            byte[] bytes = baos.toByteArray();
            byte[] cipherText = cipherAesEncrypt.doFinal(bytes);

            DatagramPacket datagram = new DatagramPacket(cipherText, cipherText.length, receiver);
            this.socket.send(datagram);
        } catch (Exception var7) {
            throw new RuntimeException(var7);
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
            byte[] original = cipherAesDecrypt.doFinal(datagram.getData());
            datagram.setData(original);
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            e.printStackTrace();
        }

        return readDatagram(datagram);
    }

    @Override
    public Message nonBlockingReceive() {
        DatagramPacket datagram = new DatagramPacket(new byte[1024], 1024);

        try {
            this.socket.setSoTimeout(1);
        } catch (SocketException var7) {
            throw new RuntimeException(var7);
        }

        boolean timeoutExpired;
        try {
            this.socket.receive(datagram);
            timeoutExpired = false;
        } catch (SocketTimeoutException var5) {
            timeoutExpired = true;
        } catch (IOException var6) {
            throw new RuntimeException(var6);
        }

        try {
            this.socket.setSoTimeout(0);
        } catch (SocketException var4) {
            throw new RuntimeException(var4);
        }

        try {
            byte[] original = cipherAesDecrypt.doFinal(datagram.getData());
            datagram.setData(original);
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            e.printStackTrace();
        }

        return timeoutExpired ? null : this.readDatagram(datagram);
    }

    private Message readDatagram(DatagramPacket datagram) {
        try {
            ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(datagram.getData()));
            return new Message((Serializable)ois.readObject(), (InetSocketAddress)datagram.getSocketAddress());
        } catch (Exception var3) {
            throw new RuntimeException(var3);
        }
    }

}
