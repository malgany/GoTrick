package main

import "testing"

func TestGreeting(t *testing.T) {
	if got := Greeting("GoTrick"); got != "Hello, GoTrick!" {
		t.Fatalf("unexpected greeting: %q", got)
	}
}
