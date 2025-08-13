package metrics

import (
	"log"
	"net/http"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promhttp"
)

var (
	MessagesProcessed = prometheus.NewCounter(
		prometheus.CounterOpts{
			Name: "processor_messages_total",
			Help: "Total number of messages processed",
		},
	)
)

func init() {
	prometheus.MustRegister(MessagesProcessed)
}

func Serve(addr string) {
	http.Handle("/metrics", promhttp.Handler())
	log.Printf("Metrics server listening on %s", addr)
	if err := http.ListenAndServe(addr, nil); err != nil {
		log.Fatalf("metrics server error: %v", err)
	}
}
