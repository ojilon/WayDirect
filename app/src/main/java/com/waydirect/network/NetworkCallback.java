// File path: com/example/WayDirect/network/NetworkCallback.java
package com.waydirect.network;

/**
 * An interface (contract) letting background threads push live 
 * terminal logs out to the MainActivity without knowing MainActivity exists.
 */
public interface NetworkCallback {
    void onConsoleUpdate(String outputText);
    void onOperationComplete(String finalResult);
}
