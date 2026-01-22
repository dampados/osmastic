package main

import (
	"fmt"
	"time"
)

func task(ch chan<- int, num int) {
	ch <- num + 1
}

func task2(ch chan<- string, str string) {

	for i := 1; i <= 5; i++ {
		ch <- str + " "
	}
	close(ch)

}

func main() {
	testChan := make(chan int)
	test2Chan := make(chan string, 10)
	defer close(test2Chan)

	go task(testChan, 5)
	go task2(test2Chan, "assmen!")
	time.Sleep(1 * time.Second)
	fmt.Println(<-testChan)
	for ass := range test2Chan {
		fmt.Print(ass)
	}
}
