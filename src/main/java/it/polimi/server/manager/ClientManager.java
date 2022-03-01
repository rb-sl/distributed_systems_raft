package it.polimi.server.manager;

import it.polimi.exceptions.NotLeaderException;
import it.polimi.networking.ClientResult;
import it.polimi.networking.messages.ReadRequest;
import it.polimi.networking.messages.WriteRequest;
import it.polimi.server.Server;

import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.Map;

/**
 * Server component handling clients
 */
public class ClientManager {
    /**
     * The owner
     */
    private final Server server;

    /**
     * Cache storing the last response to each client
     */
    private final Map<String, ClientResult> clientCache;
    /**
     * Synchronization object for clientCache
     */
    private final Object clientCacheSync = new Object();

    /**
     * Map storing the response associated to a requestNumber
     */
    private static final Map<Integer, ClientResult> clientResponse = new HashMap<>();
    /**
     * Synchronization object for clientResponse
     */
    private static final Object clientResponseSync = new Object();

    public ClientManager(Server server) {
        this.server = server;
        this.clientCache = new HashMap<>();
    }

    /**
     * Pauses the client thread until a response is available 
     * @param currentRequest The client request number
     * @return The execution result
     * @throws NotLeaderException If the server is not the leader
     */
    private ClientResult waitResponse(Integer currentRequest) throws NotLeaderException {
        synchronized (clientResponseSync) {
            while(!clientResponse.containsKey(currentRequest)) {
                try {
                    clientResponseSync.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            ClientResult response = clientResponse.remove(currentRequest);
            if(response.getStatus() == ClientResult.Status.NOTLEADER) {
                throw new NotLeaderException(this.server.getId() + " is not a leader", this.server.getLeader());
            }
            return response;
        }
    }

    /**
     * Retrieves the last result for the client
     * @param clientId The client
     * @param clientRequestNumber The request number
     * @return The cached result
     */
    private Integer getCachedResult(String clientId, Integer clientRequestNumber) {
        // If it receives a command whose serial number has already been executed, it responds
        // immediately without re-executing the request.
        ClientResult latestResult;
        synchronized (clientCacheSync) {
            latestResult = clientCache.get(clientId);
        }
        if(latestResult != null) {
            Integer latestRequest = latestResult.getClientRequestNumber();
            if(latestRequest.equals(clientRequestNumber)) {
                return latestResult.getResult();
            }
        }
        return null;
    }

    /**
     * Execution of the client's read request
     * @param clientId The client
     * @param clientRequestNumber The request number
     * @param variable The variable to read
     * @return The variable value
     * @throws NotLeaderException If the server is not the leader
     */
    public Integer read(String clientId, Integer clientRequestNumber, String variable) throws NotLeaderException {
        Integer latestResponse = getCachedResult(clientId, clientRequestNumber);
        if(latestResponse != null) {
            return latestResponse;
        }

        Integer currentRequest = server.nextRequestNumber();
        server.enqueue(new ReadRequest(currentRequest, clientRequestNumber, variable));

        ClientResult response = waitResponse(currentRequest);
        synchronized (clientCacheSync) {
            clientCache.put(clientId, response);
        }
        return response.getResult();
    }

    /**
     * Execution of the client's write request
     * @param clientId The client
     * @param clientRequestNumber The request number
     * @param variable The variable to write
     * @param value The value to write
     * @return The number of written variables
     * @throws NotLeaderException If the server is not the leader
     */
    public Integer write(String clientId, Integer clientRequestNumber, String variable, Integer value) throws NotLeaderException {
        Integer latestResponse = getCachedResult(clientId, clientRequestNumber);
        if(latestResponse != null) {
            return latestResponse;
        }

        Integer currentRequest = server.nextRequestNumber();
        server.enqueue(new WriteRequest(currentRequest, clientRequestNumber, variable, value));

        // An answer is provided only after that the request has been applied to the
        // state machine
        ClientResult response = waitResponse(currentRequest);
        synchronized (clientCacheSync) {
            clientCache.put(clientId, response);
        }
        return response.getResult();
    }

    /**
     * Creates a successful response
     * @param internalRequestNumber The receipt associated to the server message
     * @param clientRequestNumber The number of the client request
     * @param result The result to return
     */
    public void clientRequestComplete(Integer internalRequestNumber, Integer clientRequestNumber, Integer result) {
        addClientResponse(internalRequestNumber, clientRequestNumber, result, ClientResult.Status.OK);
    }

    /**
     * Creates an error response
     * @param internalRequestNumber The receipt associated to the server message
     * @param clientRequestNumber The number of the client request
     * @param status The error message
     */
    public void clientRequestError(Integer internalRequestNumber, Integer clientRequestNumber, ClientResult.Status status) {
        addClientResponse(internalRequestNumber, clientRequestNumber, null, status);
    }

    /**
     * Creates the response
     * @param internalRequestNumber The receipt associated to the server message
     * @param clientRequestNumber The number of the client request
     * @param result The result to return
     * @param status The error message
     */
    private void addClientResponse(Integer internalRequestNumber, Integer clientRequestNumber, Integer result, ClientResult.Status status) {
        synchronized (clientResponseSync) {
            clientResponse.put(internalRequestNumber, new ClientResult(clientRequestNumber, result, status));
            clientResponseSync.notifyAll();
        }
    }
}
