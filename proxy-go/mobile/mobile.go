// Package mobile exposes the unmodified https-proxy client to gomobile.
package mobile

import (
	"log"
	"os"

	"github.com/justlovediaodiao/https-proxy/client"
)

func Start(listen, server, password, cert string) error {
	log.SetOutput(os.Stderr)
	return client.Start(&client.Config{
		Listen: listen, Protocol: "socks", Server: server,
		Password: password, Cert: cert,
	})
}

func Stop() error {
	return client.Close()
}
