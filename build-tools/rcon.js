// 极简 RCON 客户端（开发期验证用，不随模组发布）
// 用法：node build-tools/rcon.js <password> <command> [command...]
const net = require('net');

const pass = process.argv[2];
const cmds = process.argv.slice(3);

function packet(id, type, body) {
  const b = Buffer.from(body, 'ascii');
  const head = Buffer.alloc(12);
  head.writeInt32LE(b.length + 10, 0);
  head.writeInt32LE(id, 4);
  head.writeInt32LE(type, 8);
  return Buffer.concat([head, b, Buffer.from([0, 0])]);
}

const sock = net.connect(25575, '127.0.0.1');
let buffer = Buffer.alloc(0);

sock.on('connect', () => {
  sock.write(packet(1, 3, pass));
  cmds.forEach((cmd, i) => {
    setTimeout(() => sock.write(packet(i + 2, 2, cmd)), 600 + i * 300);
  });
  setTimeout(() => sock.end(), 600 + cmds.length * 300 + 400);
});

sock.on('data', (chunk) => {
  buffer = Buffer.concat([buffer, chunk]);
  while (buffer.length >= 4) {
    const len = buffer.readInt32LE(0);
    if (len < 10 || buffer.length < len + 4) return;
    const id = buffer.readInt32LE(4);
    const type = buffer.readInt32LE(8);
    const body = buffer.subarray(12, 4 + len - 2).toString('utf8');
    buffer = buffer.subarray(4 + len);
    if (type === 2 && id > 1) console.log(body.trimEnd());
  }
});

sock.on('close', () => {});
sock.on('error', (e) => {
  console.error('RCON error:', e.message);
});
setTimeout(() => process.exit(0), 20000);
