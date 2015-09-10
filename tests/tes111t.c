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
typedef struct packets{
	int sign;    
	int fd;  
	int flags; 
	mode_t mode; 
	size_t count; 
	off_t offset;  
	int whence;  
	ssize_t ret_read; 
	int error; 
	struct stat stat_buf; 
	char pathname[200];
}packet;
int fd=-1;
void sigchld_handler(int sig)
{
	while (waitpid(-1, 0, WNOHANG) > 0)
		;
	return;
}
void do_service(int sessfd){
	packet packet1 ;

	int rv;
	char *temp;
	int errornumber=0;
	char buf[MAXMSGLEN+1];

	while ( (rv=recv(sessfd, &packet1, sizeof(packet1), 0)) > 0) {
		
		int operation=packet1.sign;
		
		if (operation == 0 ) {

			fd = open(packet1.pathname,packet1.flags,packet1.mode);
			errornumber=errno;

			packet packet2;
			if(fd!=-1)
				packet2.fd = fd+10000;
			else
				packet2.fd = -1;
			packet2.error= errornumber;



			send(sessfd, &packet2, sizeof(packet2), 0);
			}  
		if (operation == 1 ) {

			printf("%s\n","write begins");
			size_t count=packet1.count;

			int fd1 =packet1.fd-10000;
			void *buffer = (void *)malloc(sizeof(char)*(count+1));
			void *tmpbuffer= buffer;
			void *finalbuf= buffer + count;
			while(tmpbuffer<finalbuf){
				rv = recv(sessfd,tmpbuffer,2000, 0);

				tmpbuffer += rv;

			}
			
			ssize_t fd2 = write(fd1,buffer,count);

			errornumber=errno;

			//printf("%d\n",fd);

			packet packet3;
			packet3.ret_read = fd2;
			packet3.error=errornumber; 
			
			send(sessfd, &packet3, sizeof(packet3), 0);
			
			free(buffer);


		}    
		if (operation ==2 ) {
			
			int fd1 =packet1.fd-10000;
			fd = close(fd1);
			errornumber=errno;
			packet packet3;
			packet3.fd = fd;
			packet3.error=errornumber; 
			send(sessfd, &packet3, sizeof(packet3), 0);
			
		}   
		if (operation==3) {


			size_t count=packet1.count;
			temp =(char *) malloc(sizeof(char)*count);
			int fd1 =packet1.fd-10000;
			ssize_t fd2 = read(fd1,temp,count);

			errornumber=errno;
			packet packet3;
			packet3.ret_read = fd2;
			packet3.error=errornumber;

			char *temps =(char *) malloc(sizeof(char)*count+sizeof(packet3)); 
			memcpy(temps,&packet3,sizeof(packet3));

			memcpy(temps+sizeof(packet3),temp,count);
			send(sessfd, temps, sizeof(char)*count+sizeof(packet3), 0);
			free(temp);
			free(temps);
			
		}  
		if (operation==4 ) {
			off_t offsets = lseek(packet1.fd-10000,packet1.offset,packet1.whence);
			errornumber=errno;
			packet packet3;
			packet3.offset = offsets;
			packet3.error=errornumber; 

			send(sessfd, &packet3, sizeof(packet3), 0);
			
		}    
		if (operation ==5 ) {
			fd = unlink(packet1.pathname);
			errornumber=errno;
			packet packet3;
			packet3.fd = fd;
			packet3.error=errornumber; 
			send(sessfd, &packet3, sizeof(packet3), 0);
			
		} 
		if (operation==6) {
			size_t count=packet1.count;
			temp =(char *) malloc(sizeof(char)*count);
			int fd1 =packet1.fd-10000;
			off_t *s = &packet1.offset;
			fd = getdirentries(fd1,temp,count,s);
			printf("%s\n",temp);
			printf("%s\n","haha");
			errornumber=errno;
			packet packet3;
			packet3.fd = fd;
			packet3.error=errornumber;

			char *temps =(char *) malloc(sizeof(char)*count+sizeof(packet3)); 
			memcpy(temps,&packet3,sizeof(packet3));
			memcpy(temps+sizeof(packet3),temp,count);
			
			send(sessfd, temps, sizeof(char)*count+sizeof(packet3), 0);
			free(temp);
			free(temps);


		} 
		if(operation==7){
			struct stat recbuf;
			int fd2 =__xstat(packet1.fd, packet1.pathname, &recbuf);
			packet packet2;
			packet2.fd = fd2;
			packet2.stat_buf = recbuf;
			packet2.error = errno;
			send(sessfd,&packet2,sizeof(packet2),0);
		}              
	}
	if (rv<0) err(1,0);
}



int main(int argc, char**argv) {
          printf("%s\n","haha");
        char *b = "haha";
        int a = open("pony",O_RDONLY);
       printf("%d\n",errno);
        int c= write(a,&b,4);
        printf("%d\n",errno);
      printf("%d\n",c);
        int d=close(a);        
	printf("%d\n",d);
       printf("%d\n",errno);
	return 0;
}

