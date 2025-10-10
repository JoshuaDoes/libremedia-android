package main

import (
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
)

var (
	baseURL       = "https://joshuado.es/android/ffmpeg"
	architectures = []string{"arm64-v8a", "armeabi-v7a", "x86", "x86_64"}
	binFiles      = []string{"ffmpeg", "ffprobe"}
	libFiles      = []string{
		/*"libavcodec.so",
		"libavdevice.so",
		"libavfilter.so",
		"libavformat.so",
		"libavutil.so",
		"libswresample.so",
		"libswscale.so",*/
		"libc++_shared.so",
	}
)

func main() {
	for _, arch := range architectures {
		downloadDir := filepath.Join(arch)
		if err := os.MkdirAll(downloadDir, 0755); err != nil {
			fmt.Printf("Failed to create directory %s: %v\n", downloadDir, err)
			continue
		}

		// Download bin files
		for _, file := range binFiles {
			outputPath := filepath.Join(downloadDir, file+".so")
			url := fmt.Sprintf("%s/%s/bin/%s", baseURL, arch, file)
			if err := downloadFile(url, outputPath); err != nil {
				fmt.Printf("Failed to download %s: %v\n", url, err)
			}
		}

		// Download lib files
		for _, file := range libFiles {
			outputPath := filepath.Join(downloadDir, file)
			url := fmt.Sprintf("%s/%s/lib/%s", baseURL, arch, file)
			if err := downloadFile(url, outputPath); err != nil {
				fmt.Printf("Failed to download %s: %v\n", url, err)
			}
		}
	}
}

func downloadFile(url, outputPath string) error {
	fmt.Printf("Downloading: %s\n", outputPath)

	resp, err := http.Get(url)
	if err != nil {
		return fmt.Errorf("error making GET request: %v", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("bad status: %s", resp.Status)
	}

	out, err := os.Create(outputPath)
	if err != nil {
		return fmt.Errorf("error creating file: %v", err)
	}
	defer out.Close()

	_, err = io.Copy(out, resp.Body)
	if err != nil {
		return fmt.Errorf("error writing to file: %v", err)
	}

	return nil
}
