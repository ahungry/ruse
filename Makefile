all: hello

hello:
	gcc -Wall hello.c `pkg-config fuse3 --cflags --libs` -o hello
