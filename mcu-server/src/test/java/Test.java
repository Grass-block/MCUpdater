import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

interface Test{
    class BiTree{
        private int data;
        private int weight;
        private BiTree left;
        private BiTree right;

        public BiTree(int data, int weight, BiTree left, BiTree right){
            this.data = data;
            this.weight = weight;
            this.left = left;
            this.right = right;
        }

        @Override
        public int hashCode() {
            return data;
        }
    }

    class Encode{
        private int digit;
        private int limit;

        public Encode(int digit, int limit){
            this.digit = digit;
            this.limit = limit;
        }
    }

    //利用逐层递归构建二叉树
    static void buildTree(Set<BiTree> weights){
        if(weights.size()==1){
            return;
        }

        var min1 = new BiTree((byte) 0,Integer.MAX_VALUE,null,null);
        var min2 = new BiTree((byte) 0,Integer.MAX_VALUE,null,null);

        for (var w:weights){
            if(w.weight <= min1.weight){
                min1 = w;
            }
        }

        for (var w:weights){
            if(w.weight==min1.weight){
                continue;
            }
            if(w.weight <= min2.weight){
                min2 = w;
            }
        }

        weights.remove(min1);
        weights.remove(min2);

        weights.add(new BiTree(min1.data+128+min2.data+128,min1.weight+min2.weight,min1,min2));
        buildTree(weights);
    }

    //解析二叉树生成对应编码
    static void encodeWeights(BiTree entry,Encode[] encodes,int code,int lim){
        if (entry.left.left == null) {
            encodes[entry.left.data + 128].digit = code << 1;
            encodes[entry.left.data + 128].limit = lim + 1;
        } else {
            encodeWeights(entry.left, encodes, code << 1, lim + 1);
        }

        if (entry.right.left == null) {
            encodes[entry.right.data + 128].digit = (code << 1) | 1;
            encodes[entry.right.data + 128].limit = lim + 1;
        } else {
            encodeWeights(entry.right, encodes, (code << 1) | 1, lim + 1);
        }
    }

    //根据输入字节创建哈夫曼树
    static void huffmanEncodeTree(byte[] data){
        long last = System.currentTimeMillis();
        var weights = new HashMap<Integer,BiTree>();

        for (var i=0;i< data.length;i++){
            int finalI = i;
            weights.computeIfAbsent((int) data[i],(k)->new BiTree(data[finalI],0,null,null)).weight++;
        }
        //得到各个数字的权重列表

        var encodes = new Encode[256];
        for (var i=0;i<256;i++){
            encodes[i] = new Encode(0,0);
        }

        var tree = new HashSet<>(weights.values());
        buildTree(tree);

        encodeWeights(tree.iterator().next(),encodes,0,0);
        int cl=0;
        for (var i=0;i<encodes.length;i++){
            var e=encodes[i];
            var node = weights.get(i-128);
            if(node==null){
                continue;
            }
            cl=cl+node.weight*e.limit;
            System.out.printf("%d -> %s,weight=%d%n",i-128, Integer.toBinaryString(e.digit),node.weight);
        }

        var time=System.currentTimeMillis() - last;
        var l1=data.length*8;
        var p =100-(int)((float)cl*100/l1);
        System.out.printf("原始长度: %d 压缩长度: %d%n 压缩度:%d%% 时间:%dms",l1,cl,p,time);

    }

    static void main(String[] args) {
        byte[] data = new byte[1048576];
        int i = 0;

        for (int n = 0; n < 524288; n++) data[i++] = 'A';
        for (int n = 0; n < 262144; n++) data[i++] = 'B';
        for (int n = 0; n < 262142; n++) data[i++] = 'C';
        data[i++] = 'D';
        data[i] = 'E';

        //new Random().nextBytes(data);
        huffmanEncodeTree(data);
    }
}