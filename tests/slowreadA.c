#include <stdio.h>
#include <stdlib.h>
#include <arpa/inet.h>
#include <sys/types.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <string.h>
#include <unistd.h>
#include <err.h>
#include <errno.h>
#include <signal.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <netdb.h>
#define MAXMSGLEN 10000

int main(int argc, char**argv) {
	/*errno = 0;
 	char *b = "hafsfsdha";
        int a = open("Pony",O_RDONLY);
       printf("%d\n",errno);
	errno = 0;
        int c= write(a,&b,1024);
	 printf("%d\n",errno);
	errno = 0;
	int d = close(a);
	 printf("%d\n",errno);
	errno = 0;
      int e = open("foo",O_RDWR);
       printf("%d\n",errno);
	errno = 0;
	int f = open("Pony",O_EXCL);
	 printf("%d\n",errno);	
	errno = 0;
	lseek(a, 970, SEEK_SET);
	printf("%d\n",errno);*/
//	char *b = "woshidasb";
//	int a = open("foo",O_RDWR);
  //      int c= write(a,b,strlen(b));
	char bufA[8];
	char bufB[8];
	char bufC[8];
	char bufD[8];
	char bufE[8];
	char bufF[8];
	char bufG[8];
	char bufH[8];
	int fileA = open("A",O_RDONLY);
	//slow read A. do not close.
	int rA = read(fileA, bufA, 8);
	sleep(20);
	//read and close B
	int fileB = open("B",O_RDONLY);
	int rB = read(fileB, bufB, 8);
	close(fileB);
	  //read and close C
        int fileC = open("C",O_RDONLY);
        int rC = read(fileC, bufC, 8);
        close(fileC);
	 //read and close D
        int fileD = open("D",O_RDONLY);
        int rD = read(fileD, bufD, 8);
        close(fileD);
	 //read and close E
        int fileE = open("E",O_RDONLY);
        int rE = read(fileE, bufE, 8);
        close(fileE);
	 //read and close F
        int fileF = open("F",O_RDONLY);
        int rF = read(fileF, bufF, 8);
        close(fileF);
	 //read and close G
        int fileG = open("G",O_RDONLY);
        int rG = read(fileG, bufG, 8);
        close(fileG);
	 //read and close H
        int fileH = open("H",O_RDONLY);
        int rH = read(fileH, bufH, 8);
        close(fileH);

	close(fileA);
	return 0;
}

