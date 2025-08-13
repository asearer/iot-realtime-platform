package websocket

import (
	"encoding/json"
	"log"
	"net/http"

	"github.com/gorilla/websocket"
)

var upgrader = websocket.Upgrader{
	CheckOrigin: func(r *http.Request) bool {
		// Allow connections from any origin in development
		// In production, you should restrict this to known origins
		return true
	},
}

type Server struct {
	hub  *Hub
	addr string
}

type Message struct {
	Type      string      `json:"type"`
	Timestamp int64       `json:"timestamp"`
	Data      interface{} `json:"data"`
}

func NewServer(addr string) *Server {
	hub := NewHub()
	return &Server{
		hub:  hub,
		addr: addr,
	}
}

func (s *Server) Run() {
	// Start the hub
	go s.hub.Run()

	// Setup HTTP routes
	http.HandleFunc("/ws", s.handleWebSocket)
	http.HandleFunc("/health", s.handleHealth)

	log.Printf("WebSocket server starting on %s", s.addr)
	log.Fatal(http.ListenAndServe(s.addr, nil))
}

func (s *Server) handleWebSocket(w http.ResponseWriter, r *http.Request) {
	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		log.Printf("WebSocket upgrade error: %v", err)
		return
	}

	client := NewClient(s.hub, conn)
	s.hub.register <- client

	// Start client goroutines
	go client.WritePump()
	go client.ReadPump()
}

func (s *Server) handleHealth(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	json.NewEncoder(w).Encode(map[string]interface{}{
		"status":            "healthy",
		"connected_clients": len(s.hub.clients),
	})
}

func (s *Server) BroadcastAlert(alert interface{}) {
	message := Message{
		Type:      "alert",
		Timestamp: 0, // Will be set by the client
		Data:      alert,
	}

	if data, err := json.Marshal(message); err == nil {
		s.hub.broadcast <- data
	} else {
		log.Printf("Failed to marshal alert message: %v", err)
	}
}

func (s *Server) BroadcastMetric(metric interface{}) {
	message := Message{
		Type:      "metric",
		Timestamp: 0,
		Data:      metric,
	}

	if data, err := json.Marshal(message); err == nil {
		s.hub.broadcast <- data
	} else {
		log.Printf("Failed to marshal metric message: %v", err)
	}
}

func (s *Server) BroadcastDeviceStatus(status interface{}) {
	message := Message{
		Type:      "device_status",
		Timestamp: 0,
		Data:      status,
	}

	if data, err := json.Marshal(message); err == nil {
		s.hub.broadcast <- data
	} else {
		log.Printf("Failed to marshal device status message: %v", err)
	}
}

func (s *Server) GetConnectedClients() int {
	return len(s.hub.clients)
}

func (s *Server) Stop() {
	// Close all client connections
	for client := range s.hub.clients {
		close(client.send)
		client.conn.Close()
	}
}
