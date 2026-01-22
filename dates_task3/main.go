package main

import (
	"bufio"
	"fmt"
	"io"
	"os"
	"strings"
	"time"
)

func main() {

	bufReader := bufio.NewReader(os.Stdin)
	layout := "02.01.2006 15:04:05" //13.03.2018 14:00:15

	scannedString, err := bufReader.ReadString('\n')
	//if err != nil { fmt.Fprintln(os.Stderr, err); return }
	if err != nil && err != io.EOF {
		panic(err)
	}

	//scannedString = scannedString[:len(scannedString)]
	scannedString = strings.TrimRight(scannedString, "\r\n")

	dateSlice := strings.Split(scannedString, ",")

	firstDate, err := time.Parse(layout, dateSlice[0])
	//if err != nil { fmt.Fprintln(os.Stderr, err); return }
	if err != nil && err != io.EOF {
		panic(err)
	}

	secondDate, err := time.Parse(layout, dateSlice[1])
	//if err != nil { fmt.Fprintln(os.Stderr, err); return }
	if err != nil && err != io.EOF {
		panic(err)
	}

	if firstDate.Before(secondDate) {
		dur := secondDate.Sub(firstDate)
		fmt.Println(dur.Round(time.Second))
	} else {
		dur := firstDate.Sub(secondDate)
		fmt.Println(dur.Round(time.Second))
	}

}
