package main

import (
	"bufio"
	"fmt"
	"io"
	"os"
	"runtime"
	"strings"
	"time"
)

// вам это понадобится
const now = 1589570165

func main() {

	bufReader := bufio.NewReader(os.Stdin)

	inputString, err := bufReader.ReadString('\n')
	if err != nil && err != io.EOF {
		panic(err)
	}

	mapOchka := map[string]string{

		" час.": "h",
		" мин.": "m",
		" сек.": "s",
	}

	for old, new := range mapOchka {
		inputString = strings.ReplaceAll(inputString, old, new)
	}
	inputString = strings.ReplaceAll(inputString, " ", "")
	inputString = strings.TrimSpace(inputString)

	//fmt.Fprintln(os.Stdout, inputString)

	/// Starting magic with const date and parsing to time.Duration \\\

	constTime := time.Unix(now, 0).UTC()

	dur, err := time.ParseDuration(inputString)
	if err != nil && err != io.EOF {
		panic(err)
	}

	finalTime := constTime.Add(dur)
	fmt.Fprintln(os.Stdout, finalTime.Format(time.UnixDate))

	fmt.Println("GOMAXPROCS =", runtime.GOMAXPROCS(0))
	fmt.Println("Number of CPUs =", runtime.NumCPU())

}
