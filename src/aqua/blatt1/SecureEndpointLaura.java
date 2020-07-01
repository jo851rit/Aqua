package aqua.blatt1;

import aqua.blatt1.common.msgtypes.DummyMessage;
import aqua.blatt1.common.msgtypes.KeyExchangeMessage;
import messaging.Endpoint;
import messaging.Message;

import javax.crypto.*;
import java.io.IOException;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.security.*;
import java.util.HashMap;
import java.util.Map;

public class SecureEndpointLaura extends Endpoint {

	private Endpoint endpoint;
	private Cipher decrypt;
	private Cipher encrypt;
	private KeyPair keyPair;
	Map<InetSocketAddress, Key> keys = new HashMap<>();

	public SecureEndpointLaura(int port){

		endpoint = new Endpoint(port);
		initializeEndpoint();
	}

	public SecureEndpointLaura() {

		endpoint = new Endpoint();
		initializeEndpoint();

	}

	private void initializeEndpoint(){
		try {
			KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
			keyPairGenerator.initialize(4096);
			keyPair = keyPairGenerator.generateKeyPair();
			decrypt = Cipher.getInstance("RSA");
			decrypt.init(Cipher.DECRYPT_MODE,keyPair.getPrivate());
			

		} catch (NoSuchAlgorithmException | InvalidKeyException | NoSuchPaddingException e) {
			e.printStackTrace();
		}
	}



	@Override
	public void send(InetSocketAddress address, Serializable payload){

		if(!keys.containsKey(address)) {
			endpoint.send(address, new KeyExchangeMessage(keyPair.getPublic()));
			Message message = endpoint.blockingReceive();
			if (message.getPayload() instanceof KeyExchangeMessage) {
				KeyExchangeMessageHandler(message);
				encrypt(address,payload);
			}
		}else encrypt(address, payload);
	}

	private void encrypt(InetSocketAddress address,Serializable payload){
		try {
			encrypt = Cipher.getInstance("RSA");
			encrypt.init(Cipher.ENCRYPT_MODE,keys.get(address));
			SealedObject s = new SealedObject(payload,encrypt);
			endpoint.send(address,s);
		} catch (IllegalBlockSizeException | IOException | NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException e) {
			e.printStackTrace();
		}
	}

	private Message decrypt(Message crypto){

		if(crypto.getPayload() instanceof KeyExchangeMessage){
			if(!keys.containsKey(crypto.getSender())) KeyExchangeMessageHandler(crypto);

			endpoint.send(crypto.getSender(),new KeyExchangeMessage(keyPair.getPublic()));
			return new Message(new DummyMessage(),crypto.getSender());
		}

		SealedObject cryptoPayload = (SealedObject) crypto.getPayload();
		try {
			Serializable serializable = (Serializable) cryptoPayload.getObject(decrypt);

			return new Message(serializable,crypto.getSender());
		} catch (IllegalBlockSizeException | ClassNotFoundException | IOException | BadPaddingException e) {
			e.printStackTrace();
		}
		return new Message(null,null);
	}

	public void KeyExchangeMessageHandler(Message m){

		keys.put(m.getSender(),((KeyExchangeMessage) m.getPayload()).getPublicKey());
	}

	@Override
	public Message blockingReceive() {
		Message crypto = endpoint.blockingReceive();
		return decrypt(crypto);
	}

	@Override
	public Message nonBlockingReceive() {
		Message crypto = endpoint.nonBlockingReceive();
		return decrypt(crypto);
	}

}
