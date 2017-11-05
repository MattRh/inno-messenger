  # !/usr/bin/env python


import socket
import tinydb



TCP_IP = '127.0.0.1'

TCP_PORT = 5005

s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)

s.bind((TCP_IP, TCP_PORT))


def recvall(sock):
    BUFF_SIZE = 1024 # 1 KiB
    data = b''
    while True:
        part = sock.recv(BUFF_SIZE)
        data += part
        if len(part) < BUFF_SIZE:
            # either 0 or end of data
            break
    return data

while 1:
    s.listen(1)
    conn, addr = s.accept()
    print('Connection address:', addr)

    data = recvall(conn)
    if not data:
        break
    for byte in data:
        print("received data:", byte)
    conn.send(data)  # echo

conn.close()