package edu.sjsu.cmpe.procurement.jobs;

import java.util.ArrayList;
import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.Message;
import javax.jms.TextMessage;

import org.eclipse.jetty.util.ajax.JSONDateConvertor;
import org.fusesource.stomp.jms.StompJmsConnectionFactory;
import org.fusesource.stomp.jms.StompJmsDestination;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParser;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

import de.spinscale.dropwizard.jobs.Job;
import de.spinscale.dropwizard.jobs.annotations.Every;
import edu.sjsu.cmpe.procurement.ProcurementService;
import edu.sjsu.cmpe.procurement.domain.Book;

/**
 * This job will run at every 5 minutes.
 */
@Every("300s")
public class ProcurementSchedulerJob extends Job {
	private final Logger log = LoggerFactory.getLogger(getClass());

	@Override
	public void doJob(){

		try {

			ArrayList<Long> longArray = new ArrayList<Long>();
			longArray = consumeQueueMessages();
			log.info("Messages from Queue = {}", longArray.size());
			if (!longArray.isEmpty()) {
				postMessages(longArray);
				getMessages();
			}

		} catch (Exception e) {
			// System.out.println(e.printStackTrace());
		}
		String strResponse = ProcurementService.jerseyClient.resource(
				"http://ip.jsontest.com/").get(String.class);
		log.debug("Response from jsontest.com: {}", strResponse);

	}

	public ArrayList<Long> consumeQueueMessages() throws JMSException {
		String user = "admin";
		String password = "password";
		String host = "54.215.133.131";
		int port = 61613;
		
		ArrayList<Long> isbnArray = new ArrayList<Long>();

		int i = 0;
		String queue = "/queue/79417.book.orders";
		StompJmsConnectionFactory factory = new StompJmsConnectionFactory();
		factory.setBrokerURI("tcp://" + host + ":" + port);

		Connection connection = factory.createConnection(user, password);
		connection.start();
		Session session = connection.createSession(false,
				Session.AUTO_ACKNOWLEDGE);
		Destination dest = new StompJmsDestination(queue);

		MessageConsumer consumer = session.createConsumer(dest);
		System.out.println("Fetching messages from " + queue + "...");

		long waitUntil = 5000; 
		while(true) {
		    Message msg = consumer.receive(waitUntil);
		    if( msg instanceof  TextMessage ) {
		           String body = ((TextMessage) msg).getText();
		           System.out.println("Received message = " + body);
		           String isbnSplitArray[] = body.split(":");
					Long isbnValue = Long.parseLong(isbnSplitArray[1]);
					System.out.println(isbnValue);
					isbnArray.add(i, isbnValue);
					i++;
		    } else if (msg == null) {
		          System.out.println("Existing due to timeout - " + waitUntil / 1000 + " sec");
		          break;
		    } else {
		         System.out.println("Unexpected message type: " + msg.getClass());
		    }
		} 
		connection.close();
		System.out.println("Done");
		connection.close();
		System.out.println("Done");
		return isbnArray;
	}

	public void postMessages(ArrayList<Long> longArray) {
		try {

			Client client = Client.create();
			WebResource webResource = client
					.resource("http://54.215.133.131:9000/orders");

			
			String input = "{\"id\":\"79417\",\"order_book_isbns\":"
					+ longArray + "}";

			ClientResponse response = webResource.type("application/json")
					.post(ClientResponse.class, input);

			if (response.getStatus() != 200) {
				throw new RuntimeException("Failed : HTTP error code : "
						+ response.getStatus());
			}

			System.out.println("Server result\n");
			String output = response.getEntity(String.class);
			System.out.println(output);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void getMessages() {
		String output = "";
		String user = "admin";
		String password = "password";
		String host = "54.215.133.131";
		int port = 61613;

		try {

			Client client = Client.create();

			WebResource webResource = client
					.resource("http://54.215.133.131:9000/orders/79417");

			ClientResponse response = webResource.accept("application/json")
					.get(ClientResponse.class);

			if (response.getStatus() != 200) {
				throw new RuntimeException("ERROR::HTTP error code : "
						+ response.getStatus());
			}
			output = response.getEntity(String.class);
			if (!output.isEmpty()) {

				System.out.println("Server result\n");
				System.out.println(output);

				String array1[] = output.split("\\[");
				 array1 = array1[1].split("\\}");
				for (int i = 0; i < array1.length; i++) {
					array1[i] = array1[i] + "}";
					if (i != 0)
						array1[i] = array1[i].substring(2);
				}
				
				String [] array2 = new String[array1.length-1];
				for(int j=0;j<array2.length;j++){
					array2[j] = array1[j];
				}

				JSONArray nameArray = (JSONArray) JSONSerializer.toJSON(array2);

				log.info("NameArray size = {}", nameArray.size());
				if (nameArray != null) {
					for (Object jsonObject : nameArray) {
						JSONObject json = (JSONObject) JSONSerializer
								.toJSON(jsonObject);

						if (json != null) {
							log.info("Before getting ISBN");
							Integer isbn = 0;
							String title = "";
							String category = "";
							String coverImage ="";
							String data = "";
							
							if(json.get("isbn") != null){
								isbn = Integer.valueOf(json.get("isbn")	+ "");
								title = (String) json.get("title");
								category = (String) json.get("category");
								coverImage = (String) json.get("coverimage");

								data = isbn + ":\"" + title + "\":\""
										+ category + "\":\"" + coverImage + "\"";

							}
							
							log.info("Afer ISBN=" + isbn);
							
							if (category.equalsIgnoreCase("computer")) {
								String destination = "/topic/79417.book.computer";
								StompJmsConnectionFactory factory = new StompJmsConnectionFactory();
								factory.setBrokerURI("tcp://" + host + ":"
										+ port);

								Connection connection = factory
										.createConnection(user, password);
								connection.start();
								Session session = connection.createSession(
										false, Session.AUTO_ACKNOWLEDGE);
								Destination dest = new StompJmsDestination(
										destination);
								MessageProducer producer = session
										.createProducer(dest);
								TextMessage msg = session
										.createTextMessage(data);
								msg.setLongProperty("id",
										System.currentTimeMillis());
								producer.send(msg);
								connection.close();
							} else if (category.equalsIgnoreCase("management")) {
								String destination = "/topic/79417.book.management";
								StompJmsConnectionFactory factory = new StompJmsConnectionFactory();
								factory.setBrokerURI("tcp://" + host + ":"
										+ port);

								Connection connection = factory
										.createConnection(user, password);
								connection.start();
								Session session = connection.createSession(
										false, Session.AUTO_ACKNOWLEDGE);
								Destination dest = new StompJmsDestination(
										destination);
								MessageProducer producer = session
										.createProducer(dest);
								TextMessage msg = session
										.createTextMessage(data);
								msg.setLongProperty("id",
										System.currentTimeMillis());
								producer.send(msg);
								connection.close();
							} else if (category.equalsIgnoreCase("comics")) {
								String destination = "/topic/79417.book.comics";
								StompJmsConnectionFactory factory = new StompJmsConnectionFactory();
								factory.setBrokerURI("tcp://" + host + ":"
										+ port);

								Connection connection = factory
										.createConnection(user, password);
								connection.start();
								Session session = connection.createSession(
										false, Session.AUTO_ACKNOWLEDGE);
								Destination dest = new StompJmsDestination(
										destination);
								MessageProducer producer = session
										.createProducer(dest);
								TextMessage msg = session
										.createTextMessage(data);
								msg.setLongProperty("id",
										System.currentTimeMillis());
								producer.send(msg);
								connection.close();
							} else if (category
									.equalsIgnoreCase("selfimprovement")) {
								String destination = "/topic/79417.book.selfimprovement";
								StompJmsConnectionFactory factory = new StompJmsConnectionFactory();
								factory.setBrokerURI("tcp://" + host + ":"
										+ port);

								Connection connection = factory
										.createConnection(user, password);
								connection.start();
								Session session = connection.createSession(
										false, Session.AUTO_ACKNOWLEDGE);
								Destination dest = new StompJmsDestination(
										destination);
								MessageProducer producer = session
										.createProducer(dest);
								TextMessage msg = session
										.createTextMessage(data);
								msg.setLongProperty("id",
										System.currentTimeMillis());
								producer.send(msg);
								connection.close();
							}

						}
					}
				}

			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	
}