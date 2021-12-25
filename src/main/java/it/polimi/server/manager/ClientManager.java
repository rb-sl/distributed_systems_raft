package it.polimi.server.manager;

import it.polimi.exceptions.NotLeaderException;
import it.polimi.networking.ClientResult;
import it.polimi.networking.messages.ReadRequest;
import it.polimi.networking.messages.WriteRequest;
import it.polimi.server.Server;

import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.Map;

public class ClientManager {
    private final Server server;
    
    private final Map<String, ClientResult> clientCache;
    private final Object clientCacheSync = new Object();

    
    private static final Map<Integer, ClientResult> clientResponse = new HashMap<>();
    private static final Object clientResponseSync = new Object();

    public ClientManager(Server server) {
        this.server = server;
        
        this.clientCache = new HashMap<>();        
    }

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
    
    public Integer read(String clientId, Integer clientRequestNumber, String variable) throws RemoteException, NotLeaderException {
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
    
    public Integer write(String clientId, Integer clientRequestNumber, String variable, Integer value) throws RemoteException, NotLeaderException {
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

    public void clientRequestComplete(Integer internalRequestNumber, Integer clientRequestNumber, Integer result) {
        addClientResponse(internalRequestNumber, clientRequestNumber, result, ClientResult.Status.OK);
    }

    public void clientRequestError(Integer internalRequestNumber, Integer clientRequestNumber, ClientResult.Status status) {
        addClientResponse(internalRequestNumber, clientRequestNumber, null, status);
    }

    private void addClientResponse(Integer internalRequestNumber, Integer clientRequestNumber, Integer result, ClientResult.Status status) {
        synchronized (clientResponseSync) {
            clientResponse.put(internalRequestNumber, new ClientResult(clientRequestNumber, result, status));
            clientResponseSync.notifyAll();
        }
    }
}
