package nio;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class NioTelnetServer {
        public static final String LS_COMMAND = "\tls view all files  and directories\r\n";
        public static final String MKDIR_COMMAND = "\tmkdir create directory\r\n";
        public static final String CHANGE_NICKNAME = "\tnick change nickname\r\n";
        private static final String ROOT_NOTIFICATION = "You are already in the root directory\r\n";
        private static final String ROOT_PATH = "server";
        private static final String DIRECTORY_DOESNT_EXIST = "Directory doesn't exist\r\n";
        private static final String TOUCH_COMMAND = "\ttouch create file\r\n";
        private static final String CD_COMMAND = "\tcd move by path\r\n";
        private static final String RM_COMMAND = "\trm remove file or directory\r\n";
        private static final String COPY_COMMAND = "\tcopy copy file or directory\r\n";
        private static final String CAT_COMMAND = "\tcat open file\r\n";

        private Path currentPath = Path.of("server");
        private final Map<SocketAddress, String> clients = new HashMap<>();

        public final ByteBuffer buffer = ByteBuffer.allocate(512);

        public NioTelnetServer() throws IOException {
            ServerSocketChannel server = ServerSocketChannel.open();
            server.bind(new InetSocketAddress(5678));
            server.configureBlocking(false);
            Selector selector = Selector.open();
            server.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("Server started");

            while (server.isOpen()) {
                selector.select();

                var selectionKeys = selector.selectedKeys();
                var iterator = selectionKeys.iterator();

                while (iterator.hasNext()) {
                    var key = iterator.next();
                    if (key.isAcceptable()) {
                        handleAccept (key, selector);
                    } else if (key.isReadable()) {
                        handleRead(key, selector);
                    }
                    iterator.remove();
                }
            }
        }

        private void handleRead(SelectionKey key, Selector selector) throws IOException {
            SocketChannel channel = ((SocketChannel)key.channel());
            SocketAddress client = channel.getRemoteAddress();

            String nickname = "";

            int readBytes = channel.read(buffer);
            if (readBytes < 0) {
                channel.close();
                return;
            } else if (readBytes == 0) {
                return;
            }

            buffer.flip();
            StringBuilder sb = new StringBuilder();
            while (buffer.hasRemaining()) {
                sb.append((char) buffer.get());
            }
            buffer.clear();

            // TODO
            // touch [filename] - создание файла
            // mkdir [dirname] - создание директории
            // cd [path] - перемещение по каталогу (.. | ~ )
            // rm [filename | dirname] - удаление файла или папки
            // copy [src] [target] - копирование файла или папки
            // cat [filename] - просмотр содержимого
            // вывод nickname в начале строки

            if (key.isValid()) {
                String command = sb.toString().replace("\n","").replace("\r","");
                if("--help".equals(command)) {
                    sendMessage(LS_COMMAND, selector, client);
                    sendMessage(MKDIR_COMMAND, selector, client);
                    sendMessage(CHANGE_NICKNAME, selector, client);
                    sendMessage(TOUCH_COMMAND, selector, client);
                    sendMessage(CD_COMMAND, selector, client);
                    sendMessage(RM_COMMAND, selector, client);
                    sendMessage(COPY_COMMAND, selector, client);
                    sendMessage(CAT_COMMAND, selector, client);
                } else if("ls".equals(command)) {
                    sendMessage(getFileList().concat("\r\n"),selector,client);
                } else if (command.startsWith("nick ")) {
                    nickname = changeName(channel, command);
                } else if (command.startsWith("cd ")) {
                    replacePosition(selector, client, command);
                } else if (command.startsWith("mkdir ")) {
                    createDir(command);
                } else if (command.startsWith("touch ")) {
                    creteFile(command);
                } else if (command.startsWith("rm ")) {
                    removeFile(command);
                } else if (command.startsWith("copy ")) {
                    copyFilesOrDirectory(command);
                } else if (command.startsWith("cat ")) {
                    showFile(command,selector,client);
                }
                else if ("exit".equals(command)) {
                    System.out.println("Client logged out. IP: " + channel.getRemoteAddress());
                    channel.close();
                    return;
                }
            }
            sendName(channel, nickname);
        }

        private void showFile(String command, Selector selector, SocketAddress client) throws IOException {
            String filePath = currentPath.toString().concat("/").concat(command.split(" ")[1]);
            if (!Files.exists(Path.of(filePath))) {
                return;
            }
            System.out.println(filePath);

            Files.readAllLines(Path.of(filePath)).stream()
                    .forEach(x->{
                        try {
                            sendMessage(x.concat("\r\n"), selector,client);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
        }

        private void copyFilesOrDirectory(String command) throws IOException {
            var fileForCopy = currentPath.toString().concat("\\").concat(command.split(" ")[1]);
            var destinationPath = command.split(" ")[2];
            if (!Files.exists(Path.of(fileForCopy))) {
                return;
            }
            if (Files.isDirectory(Path.of(fileForCopy))) {
                final Path fileForCopyFinal = Path.of(fileForCopy);
                final Path destinationPathFinal = Path.of(destinationPath.concat("/").concat(command.split(" ")[1]));
                Files.walkFileTree(fileForCopyFinal, new SimpleFileVisitor<>() {
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs ) throws IOException {
                        return copy(file);
                    }
                    public FileVisitResult preVisitDirectory( Path dir, BasicFileAttributes attrs ) throws IOException {
                        return copy(dir);
                    }
                    private FileVisitResult copy( Path fileOrDir ) throws IOException {
                        Files.copy(fileOrDir,destinationPathFinal.resolve( fileForCopyFinal.relativize(fileOrDir)));
                        return FileVisitResult.CONTINUE;
                    }
                });
            } else if (Files.exists(Path.of(fileForCopy))) {
                String fileName = String.valueOf(Path.of(fileForCopy).getFileName());
                Path pathFinish = Path.of(destinationPath.concat("/").concat(fileName));
                Files.copy(Path.of(fileForCopy), pathFinish, REPLACE_EXISTING);
            }
        }

        private void removeFile(String command) {
            Path pathForDelete = Path.of(currentPath.toString().concat("/").concat(command.split(" ")[1]));
            System.out.println(pathForDelete.toString());
            try {
                Files.walkFileTree(pathForDelete, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }
                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException ignored) {}
        }

        private void creteFile(String command) throws IOException {
            Path pathForNewFile = Path.of(currentPath.toString().concat("/").concat(command.split(" ")[1]));
            if (!Files.exists(pathForNewFile)) {
                Files.createFile(pathForNewFile);
            }
        }

        private void createDir(String command) throws IOException {
            Path newPath = Path.of(currentPath.toString().concat("/" + command.split(" ")[1]));
            if (Files.exists(newPath)) {
                return;
            }
            Files.createDirectories(newPath);
            currentPath = newPath;
        }

        private void replacePosition(Selector selector, SocketAddress client, String command) throws IOException {
            String neededPathString = command.split(" ")[1];
            Path tempPath = Path.of(currentPath.toString(), neededPathString);
            if (".. ".equals(neededPathString)) {
                tempPath = currentPath.getParent();
                if (tempPath == null || !tempPath.toString().startsWith("server")) {
                    sendMessage(ROOT_NOTIFICATION, selector, client);
                } else {
                    currentPath = tempPath;
                }
            } else if ("~".equals(neededPathString)) {
                currentPath = Path.of(ROOT_PATH);
            } else {
                if (tempPath.toFile().exists()) {
                    currentPath = tempPath;
                } else  {
                    sendMessage(String.format(DIRECTORY_DOESNT_EXIST, neededPathString), selector, client);
                }
            }
        }

        private String changeName(SocketChannel channel, String command) throws IOException {
            String nickname;
            nickname = command.split(" ")[1];
            clients.put(channel.getRemoteAddress(), nickname);
            System.out.println("Client - " + channel.getRemoteAddress().toString() + "changed nickname on " + nickname);
            return nickname;
        }

        private void sendName(SocketChannel channel, String nickname) throws IOException {
            if (nickname.isEmpty()) {
                nickname = clients.getOrDefault(channel.getRemoteAddress(), channel.getRemoteAddress().toString());
            }
            String currentPathString = currentPath.toString().replace("server", "~");
            channel.write(
                    ByteBuffer.wrap(nickname.concat(">:").concat(currentPathString).concat("$")
                            .getBytes(StandardCharsets.UTF_8)
                    ));
        }

        private String getFileList() {
            return String.join(" ", new File(currentPath.toString()).list());
        }

        private void sendMessage(String message, Selector selector, SocketAddress client) throws IOException {
            for(SelectionKey key : selector.keys()) {
                if (key.isValid() && key.channel() instanceof SocketChannel) {
                    if (((SocketChannel) key.channel()).getRemoteAddress().equals(client)) {
                        ((SocketChannel) key.channel()).write(ByteBuffer.wrap(
                                message.getBytes(StandardCharsets.UTF_8)));
                    }
                }
            }
        }

        private void handleAccept(SelectionKey key, Selector selector) throws IOException {
            SocketChannel channel = ((ServerSocketChannel)key.channel()).accept();
            channel.configureBlocking(false);
            System.out.println("Client accepted. IP: " + channel.getRemoteAddress());

            channel.register(selector, SelectionKey.OP_READ);
            channel.write(ByteBuffer.wrap("Hello user!\r\n".getBytes(StandardCharsets.UTF_8)));
            channel.write(ByteBuffer.wrap("\r\nEnter --help for support info".getBytes(StandardCharsets.UTF_8)));
            sendName(channel, "");
        }

        public static void main(String[] args) throws IOException {
            new nio.NioTelnetServer();
        }
    }