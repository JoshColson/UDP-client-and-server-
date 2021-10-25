import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.io.BufferedReader;
import java.io.File;

class FinalServer 
{
	//Declare and initialise variables needed to be used across multiple methods
	static DatagramSocket serverSocket = null;
	static DatagramPacket receivePacket;
	static InetAddress IPAddress = null;
	static byte[] receiveDataByte;
	static byte[] sendDataByte;
	static BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
	static int port = 56789;
	static int initialSendCheck = 0;
	static int initialReceiveCheck = 0;
	static boolean close;
	
	public static void main(String args[]) throws Exception
	{
		
		String portIntro = "\nEnter a port to listen on, or 'd' for default settings";
		String portError = "\nOops, we had trouble with those settings, please try again with different ones";
		//Allow user to specify listening port, placed in a loop and catch block to allow for port input to be in an incorrect format
		while (true)
		{
			//Try-catch block incase errors are thrown
			try
			{
				System.out.println(portIntro);
				String userInputPort = inFromUser.readLine();
				
				//If user wants to use a default port
				if (userInputPort.equalsIgnoreCase("D"))
				{
					port= 56789;
					break;
				}
				//Change user input from string into an integer so that it can be compared with other integers
				int userInputPortInt = Integer.parseInt(userInputPort);
				
				//If the user did not chose a valid port number, inform and restart loop
				if ((userInputPortInt>65535) || (userInputPortInt<49152))
				{
					System.out.println(portError);
					continue;
				}
				
				//Otherwise, set the port as user input
				else
				{
					port = userInputPortInt;
					break;
				}
			}
			//Check if user input an invalid port number
			catch (IllegalArgumentException e)
			{
				System.out.println(portError);
				continue;
			}
			//Check for other errors in user input
			catch(Exception e)
			{
				System.out.println(portError);
				continue;
			}
		}
		//Setup message to confirm the server is waiting for connection
		System.out.println("Server Running");
		
		//Receive connection from the client and inform this to the user
		System.out.println(receiveData() + "\n");
		
		//Send connection message back to the client to confirm connection to client user
		if (!(sendData("Server Connected")))
		{
			System.out.println("failed to send");
		}
		//Initialise an int to count the loops
		int countFileNumber=0;
		

		//Start of main server loop receiving data, then packaging it together and storing it
		while(true)
		{
			//Initialise local variables, resetting them if needed from a previous file read
			
			//This variable keeps track of how many files are received in order to accurately store them
			countFileNumber++;
			
			//Variable checks whether data needs to be exported 
			boolean lastPacket=false;
			
			//Variable ensures a lost acknowledgement is simulated only once per file
			int lostAckSimulation=0;

			//Incoming data is stored inside this variable
			List<String> finalData = new ArrayList<String>();
			
			//Variable ready to track sequence numbers to ensure correct data is sent during Sliding Window of size bigger than one
			int sequenceNumber=-1;
			
			//Variable stores window size to ensure stop-and-wait is treated differently to a sliding window bigger than one
			int windowSize=0;
			
			
			//Loop for one file
			while (true)
			{
				//receive data using receiveData() method, split this data to allow easy isolation of header information
				String[] receivedData=receiveData().split(Character.toString((char)19));
				
				System.out.println("Current data received: "+String.join("-", finalData));

				//Check if client initialises closure of server
				if (receivedData[1].equalsIgnoreCase(Character.toString((char)0004)))
				{
					close();
					break;
				}
				
				//Set window size
				windowSize= Integer.parseInt(receivedData[0]);
				
				//Check whether the correct data has been received by using sequence numbers, and ensures data is sent in order when window size is bigger than one
				if (Integer.parseInt(receivedData[2]) != sequenceNumber-(windowSize-1) && (Integer.parseInt(receivedData[2]) != sequenceNumber+1) && (windowSize>1))
				{
					System.out.println("\nReceived data out of order, expecting sequence number " + (sequenceNumber+1));
					System.out.println("Discarding " + receivedData[3] + ", sequence number " + receivedData[2]);
					continue;
				}
				
				//Check whether current data marks the end of file and export is needed
				if (receivedData[1].equalsIgnoreCase(Character.toString((char)0017)))
				{
					lastPacket=true;
				}
				
				//Simulation of lost ack to show client response
				if ((Integer.parseInt(receivedData[2]) ==7) && (lostAckSimulation==0))
				{
					System.out.println("\t\t\t\t*SIMULATED LOST ACK*");
					lostAckSimulation++;
					continue;
				}

				//Store sequence number
				sequenceNumber = Integer.parseInt(receivedData[2]);
				System.out.println("\nData Received: " + receivedData[3]);
				
				//Simple loop allows for data to arrive out of order when performing Stop-And-Wait
				if(sequenceNumber>finalData.size())
				{
					for (int i=finalData.size();i<sequenceNumber;i++)
					{
						finalData.add(null);
					}
				}
				
				//Stores data in correct order of sequence number, even if data arrives out of order with Stop-And-Wait
				finalData.add(sequenceNumber, receivedData[3]);
				System.out.println("Sequence Number Received: " + sequenceNumber);
				System.out.println("Sending acknowledgement: " + "Ack " + sequenceNumber);
				
				//Send acknowledgement back to client using sendData() method
				sendData(Integer.toString(sequenceNumber));
				
				//End current file loop and export if the current packet of data was the last packet
				if (lastPacket)
				{
					break;
				}
			}		
			//Close boolean to jump out of loop at correct time
			if (close)
			{
				break;
			}
			//Change data arraylist into a string ready for export, removing null values
			String output="";
			for (String s : finalData)
			{
				if (s!=null)
				{
					output+=s;
				}
			}
			
			//Display final file received
			System.out.println("\nFinal Data Received: " + output);
			
			//Call writeFile() method, storing the final data recived, and labelling the file in order of files received
			writeFile(output, countFileNumber);
			
			//Clear saved data ready to receive another file
			finalData.clear();
		}
	}
	public static void writeFile (String data, int fileNumber)
	{
		//Try-catch block as errors can be thrown with creating files
		try 
		{
			//Create a new file variable named in order of files received
			File newFile = new File("ReceivingData-"+fileNumber+".txt");
			
			//If the file doesn't already exist, print to command line that a new file is being created
			if (newFile.createNewFile())
			{
				System.out.println("Created file: " + newFile.getName());
			}
			
			//If the file aready exists, print that the file is being rewritten
			else
			{
				System.out.println("File already Exists, rewriting...");
			}
			
			//Create a FileWriter, store the data, and then close the FileWriter when done
			FileWriter writer = new FileWriter("ReceivingData-"+fileNumber+".txt");
			writer.write(data);
			writer.close();
			System.out.println("Successfully wrote to file: "+newFile.getName());
		}
		catch (IOException e)
		{
			System.out.println("Error writing to file");
		}
	}
	private static boolean sendData (String dataToBeSent)
	{
		//Try-catch block incase errors are thrown
		try
		{
			//If this is the first time data is being sent, initialise the DataByte buffer
			if (sendDataByte==null)
			{
				sendDataByte = new byte[1024];
			}
			
			//Prepare data to be sent
			sendDataByte = dataToBeSent.getBytes();
			
			//Create data packet with data, data length, and destination
			DatagramPacket sendPacket = new DatagramPacket(sendDataByte, sendDataByte.length,IPAddress, port);
			
			//Send data
			serverSocket.send(sendPacket);
		}
		catch(Exception e)
		{
			//If error occurs, return a boolean so that error can be dealt with inside the main loop
			return false;
		}
		//If an error is not thrown, return a boolean so that the loop can confidently continue
		return true;
	}
	private static String receiveData()
	{
		//initialise/reset the incoming data variable
		String incomingData = "";
		//Try-catch block incase errors are thrown
		try
		{
			//If this is the first data to be received, configue serverSocket and other variables
			if (serverSocket==null)
			{
				serverSocket = new DatagramSocket(port);					
				receiveDataByte = new byte[1024];
				receivePacket = new DatagramPacket(receiveDataByte,receiveDataByte.length);
			}
			//Receive data
			serverSocket.receive(receivePacket);
			
			//Store received data for export out of method
			incomingData = new String(receivePacket.getData(), 0, receivePacket.getLength());

			//If this is the first data to be received, find the senders location ready for sending data back
			if (IPAddress==null)
			{
				IPAddress = receivePacket.getAddress();
				port = receivePacket.getPort();
			}
			
			//Return received data back into the main loop
			return incomingData;
		}
		catch (Exception e)
		{
			return "Error occurred receiving data";
		}
	}
	private static void close()
	{	
		//If the close() method is called, print this, and close the serverSocket. Set a boolean so that the main loop knows this has been called.
		System.out.println("\n\nClient initiated the closure of server\nGoodbye");
		serverSocket.close();
		close= true;
	}
}