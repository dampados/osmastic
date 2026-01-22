package main

import (
	"fmt"
	"time"
)

func myFunc(channel chan any) {
	// fmt.Println("hello")
	channel <- 90
	ass := <-channel
	fmt.Println(ass)
}

func main() {
	channel := make(chan any)
	go myFunc(channel)
	whoarewe := <-channel
	fmt.Println("who are we? -", whoarewe)
	whoarewe = <-channel
	fmt.Println("who are we? -", whoarewe)

	time.Sleep(2 * time.Second)
}
