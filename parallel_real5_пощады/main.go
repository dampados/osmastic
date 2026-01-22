/*


Необходимо написать функцию func merge2Channels(fn func(int) int, in1 <-chan int, in2 <- chan int, out chan<- int, n int).

Описание ее работы:

n раз сделать следующее

    прочитать по одному числу из каждого из двух каналов in1 и in2, назовем их x1 и x2.
    вычислить f(x1) + f(x2)
    записать полученное значение в out

Функция merge2Channels должна быть неблокирующей, сразу возвращая управление.

Функция fn может работать долгое время, ожидая чего-либо или производя вычисления.



Формат ввода:

    количество итераций передается через аргумент n.
    целые числа подаются через аргументы-каналы in1 и in2.
    функция для обработки чисел перед сложением передается через аргумент fn.

Формат вывода:

    канал для вывода результатов передается через аргумент out.


*/

package main

import (
	"fmt" // пакет используется для проверки выполнения условия задачи, не удаляйте его
	"sync"
	"time" // пакет используется для проверки выполнения условия задачи, не удаляйте его
)

type Result struct {
	idx   int
	value int
}

func merge2Channels(fn func(int) int, in1 <-chan int, in2 <-chan int, out chan<- int, n int) {

	wg := new(sync.WaitGroup)
	// mu := new(sync.Mutex)

	resultChan := make(chan Result, n*2)

	//go collect + fn
	go func() {

		for i := 1; i <= n*2; i += 2 {
			x1 := <-in1
			x2 := <-in2

			wg.Add(2)
			go func() { defer wg.Done(); resultChan <- Result{i, fn(x1)} }()
			go func() { defer wg.Done(); resultChan <- Result{i + 1, fn(x2)} }()
		}

		wg.Wait()
		close(resultChan)

	}()

	//go collect sort, output, close resultChan
	go func() {

		// defer close(resultChan)

		var resultSlice []Result

		// for result := range resultChan {
		for i := 0; i < n*2; i++ {
			resultSlice = append(resultSlice, <-resultChan)
		}

		// for _, elem := range resultSlice {
		// 	fmt.Println(elem)
		// }

		// fmt.Println("ХУЙ")

		findByIndex := func(slice []Result, i int) Result {

			for _, elem := range slice {
				if elem.idx == i {
					return elem
				}
			}
			return Result{999, 999}
		}

		for i := 1; i <= n*2; i += 2 {
			struct1 := findByIndex(resultSlice, i)
			struct2 := findByIndex(resultSlice, i+1)

			fmt.Println(struct1, struct2)
			out <- struct1.value + struct2.value
		}

	}()

}

func main() {
	start := time.Now()
	in1 := make(chan int)
	in2 := make(chan int)
	out := make(chan int)
	n := 10

	f := func(x int) int { // wow
		time.Sleep(time.Millisecond)
		return x
	}

	merge2Channels(f, in1, in2, out, n)

	for i := 0; i < n; i++ {
		in1 <- i
		in2 <- i + 1
	}

	i := 0
	for {
		fmt.Println(<-out)
		i++
		if i == n {
			break
		}
	}
	fmt.Println("duration:", time.Since(start))
}
