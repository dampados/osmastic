package main

func removeDuplicates(inCh <-chan string, outCh chan<- string) {
	var uniqueOnlySlice []string = make([]string, 0, 10)

	for curInputElem := range inCh {
		var foundDuplicate bool

		for _, elem := range uniqueOnlySlice {
			if elem == curInputElem {
				foundDuplicate = true
				break
			}
		}

		if foundDuplicate {
			// uniqueOnlySlice = append(uniqueOnlySlice, curInputElem)
			continue
		} else {
			uniqueOnlySlice = append(uniqueOnlySlice, curInputElem)
			outCh <- curInputElem
		}
	}

}

func main() {

}
