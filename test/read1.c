#include "syscall.h"
#include "stdio.h"

int main() {
  int fd = open("1.txt");
  char str[10];
  int num = read(fd, str, 10);
  return 0;
}
