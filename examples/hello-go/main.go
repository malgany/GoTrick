package main

import "fmt"

// Greeting is a small example for navigation, completion and refactoring.
func Greeting(name string) string {
	return fmt.Sprintf("Hello, %s!", name)
}

func main() {
	message := Greeting("GoTrick")
	fmt.Println(message)
}
