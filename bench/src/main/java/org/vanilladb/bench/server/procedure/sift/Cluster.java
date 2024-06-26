package org.vanilladb.bench.server.procedure.sift;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.*;
import java.util.List;
import java.util.PriorityQueue;

import org.vanilladb.bench.benchmarks.sift.SiftBenchConstants;
import org.vanilladb.bench.util.CustomPair;
import org.vanilladb.bench.util.CustomPairComparator;
import org.vanilladb.core.sql.IntegerConstant;
import org.vanilladb.core.sql.VectorConstant;
import org.vanilladb.core.sql.distfn.DistanceFn;
import org.vanilladb.core.sql.distfn.EuclideanFn;

public class Cluster {
    int numOfCluster;
    int numOfSample;
    // do not declare centroids to VectorConstant, cause this need to change in
    // clustering
    ArrayList<float[]> centroids = new ArrayList<float[]>();
    ArrayList<VectorConstant> sampledData = new ArrayList<VectorConstant>();
    ArrayList<VectorConstant> tempSampledData = new ArrayList<VectorConstant>();
    Random R = new Random();
    DistanceFn distFn = new EuclideanFn("i_emb");

    // dimension reduction parameter~!
    boolean DIM_REDUCTION = true;
    int N_DIM = (DIM_REDUCTION) ? 32 : 128;

    // new data needed for normalization // all have 128 instances
    VectorConstant meanOfAllDIM;
    VectorConstant standOfAllDIM; // for standard diveation

    // normalize parameter
    boolean NORM_ORIGIN_DIM = false;

    public boolean getDimReduction(){
        return DIM_REDUCTION;
    }
    public int getNDim(){
        return N_DIM;
    }
    public boolean getNormOri(){
        return NORM_ORIGIN_DIM;
    }

    public Cluster(int numOfCluster) {
        this.numOfCluster = numOfCluster;

        if (DIM_REDUCTION || NORM_ORIGIN_DIM){
            // collect sample data and set number of data to take into account
            numOfSample = 0;// to calculate the total num of sample
            try (BufferedReader br = new BufferedReader(new FileReader(SiftBenchConstants.DATASET_FILE))) {
                int id = 0;
                String vectorString;
                while (id < SiftBenchConstants.NUM_ITEMS && (vectorString = br.readLine()) != null) {
                    // make sure we don't read data out of bound
                    id++;

                    // do select here (chance = 1/20)
                    if (R.nextInt(20) != 0)
                        continue;

                    // parse the vectorstring to float array here
                    float[] float_nums = stringToVector(vectorString);

                    // store the vector to vector Constant
                    VectorConstant vc = new VectorConstant(float_nums);
                    // add the vector to sample data
                    tempSampledData.add(vc);
                    numOfSample++;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            System.out.println("samples num = " + numOfSample);

            if(NORM_ORIGIN_DIM){
                // calculate mean of all dimension by all the sample
                meanOfAllDIM = VectorConstant.zeros(SiftBenchConstants.NUM_DIMENSION);
                IntegerConstant numOfSampleConstant = new IntegerConstant(numOfSample); // for div
                for (int i=0;i<numOfSample;i++){
                    meanOfAllDIM = (VectorConstant)meanOfAllDIM.add(tempSampledData.get(i).div(numOfSampleConstant));
                }
                // calculate standard diveation of all dimension by all the sample
                standOfAllDIM = VectorConstant.zeros(SiftBenchConstants.NUM_DIMENSION);
                for (int i=0;i<numOfSample;i++){
                    standOfAllDIM = (VectorConstant)standOfAllDIM.add(tempSampledData.get(i).mul(tempSampledData.get(i)).div(numOfSampleConstant));
                }
                standOfAllDIM = (VectorConstant)standOfAllDIM.sqrt();

                // normalize all sample 
                for (int i=0;i<numOfSample;i++){ // need a lot type casting ...
                    tempSampledData.set(i, (VectorConstant)((VectorConstant) tempSampledData.get(i).sub(meanOfAllDIM)).elementDiv(standOfAllDIM));
                }
            }

            if(DIM_REDUCTION){
                // reduction
                for (int i=0;i<numOfSample;i++){ 
                    // to just 64 dim
                    sampledData.add(reduceDim(tempSampledData.get(i), SiftBenchConstants.NUM_DIMENSION));
                }
            } else {
                for (int i=0;i<numOfSample;i++){ 
                    // keep origin dim
                    sampledData.add(tempSampledData.get(i));
                }
            }
            

            // random select centroid from sample arraylist
            ArrayList<Integer> centroid_idx = new ArrayList<Integer>();
            int counter = 0;
            while (true) {
                // select a new index again if the index have been select
                int random_idx = R.nextInt(numOfSample);
                if (centroid_idx.contains(random_idx))
                    continue;

                // store the idx
                centroid_idx.add(random_idx);

                // add to centroid list
                centroids.add(sampledData.get(random_idx).copy());
                counter++;
                if (counter == numOfCluster)
                    break;
            }
            // debugging
            // debugMsgOfAllCentroids();
        } else {
            // collect sample data and set number of data to take into account
            numOfSample = 0;// to calculate the total num of sample
            try (BufferedReader br = new BufferedReader(new FileReader(SiftBenchConstants.DATASET_FILE))) {
                int id = 0;
                String vectorString;
                while (id < SiftBenchConstants.NUM_ITEMS && (vectorString = br.readLine()) != null) {
                    // make sure we don't read data out of bound
                    id++;

                    // do select here (chance = 1/20)
                    if (R.nextInt(20) != 0)
                        continue;

                    // parse the vectorstring to float array here
                    float[] float_nums = stringToVector(vectorString);

                    // store the vector to vector Constant
                    VectorConstant vc = new VectorConstant(float_nums);
                    // add the vector to sample data
                    sampledData.add(vc);
                    numOfSample++;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            System.out.println("samples num = " + numOfSample);

            // random select centroid from sample arraylist
            ArrayList<Integer> centroid_idx = new ArrayList<Integer>();
            int counter = 0;
            while (true) {
                // select a new index again if the index have been select
                int random_idx = R.nextInt(numOfSample);
                if (centroid_idx.contains(random_idx))
                    continue;

                // store the idx
                centroid_idx.add(random_idx);

                // add to centroid list
                centroids.add(sampledData.get(random_idx).copy());
                counter++;
                if (counter == numOfCluster)
                    break;
            }
            // debugging
            // debugMsgOfAllCentroids();
        }
        
    }

    // constructor for benchmark which just need centroids value and func in this
    // class
    public Cluster(ArrayList<float[]> centroids, int numOfCluster) {
        this.centroids = centroids;
        this.numOfCluster = numOfCluster;
    }

    public void setMeanStand(VectorConstant mean, VectorConstant stand){
        this.meanOfAllDIM = mean;
        this.standOfAllDIM = stand;
    }

    void clustering(int numOfRound) {
        for (int i = 0; i < numOfRound; i++) {
            // to store data as cluster
            // construct first
            ArrayList<ArrayList<VectorConstant>> dataCluster = new ArrayList<>();
            for (int n = 0; n < numOfCluster; n++) {
                ArrayList<VectorConstant> list = new ArrayList<>();
                dataCluster.add(list);
            }

            // put sample data to clusters
            for (int s = 0; s < numOfSample; s++) {
                // set query vector o distance function to the sample vector
                distFn.setQueryVector(sampledData.get(s));

                double min_dis = Double.MAX_VALUE;
                int cluster_group = 0;
                for (int j = 0; j < numOfCluster; j++) {
                    VectorConstant tempConstant = new VectorConstant(centroids.get(j));
                    double temp_dis = distFn.distance(tempConstant);
                    if (min_dis > temp_dis) {
                        min_dis = temp_dis;
                        cluster_group = j;
                    }
                }
                // add sample data to closet cluster
                dataCluster.get(cluster_group).add(sampledData.get(s));
            }

            // find new centroids
            for (int j = 0; j < numOfCluster; j++) {
                IntegerConstant samplesInCluster = new IntegerConstant(dataCluster.get(j).size());
                // System.out.println("samples num in cluster" + j + " = " + samplesInCluster);
                if ((Integer) samplesInCluster.asJavaVal() == 0)
                    continue;
                VectorConstant newCentroid = VectorConstant.zeros(N_DIM);
                for (int n = 0; n < (Integer) samplesInCluster.asJavaVal(); n++) {
                    newCentroid = (VectorConstant) newCentroid.add(dataCluster.get(j).get(n).div(samplesInCluster));
                    // System.out.println("samples = "+ newCentroid);
                }
                // update centroid to new value
                // if(DIM_REDUCTION || NORM_ORIGIN_DIM) do rounding first
                if(DIM_REDUCTION || NORM_ORIGIN_DIM) newCentroid = VectorConstant.rounding(newCentroid, N_DIM);
                centroids.set(j, newCentroid.copy());
            }
        }
        // debugging
        System.out.println("after clustering centroids =");
        for (int i = 0; i < numOfCluster; i++) {
            System.out.println("centroid" + i + " = ");
            for (int j = 0; j < N_DIM; j++) {
                System.out.print(centroids.get(i)[j] + " ");
            }
            VectorConstant temp = new VectorConstant(centroids.get(i));
            System.out.print("len = "+ temp.dimension());
            System.out.println(" ");
        }

    }

    public int getNearestCentroidId(VectorConstant vc) {
        distFn.setQueryVector(vc);

        double min_dis = Double.MAX_VALUE;
        int cluster_group = 0;
        for (int j = 0; j < numOfCluster; j++) {
            VectorConstant tempConstant = new VectorConstant(centroids.get(j));
            double temp_dis = distFn.distance(tempConstant);
            if (min_dis > temp_dis) {
                min_dis = temp_dis;
                cluster_group = j;
            }
        }
        return cluster_group;
    }

    public int getNearestCentroidId(float[] vector) {
        // set query vector o distance function to the sample vector
        VectorConstant vc = new VectorConstant(vector);
        return getNearestCentroidId(vc);
    }

    public int getNearestCentroidId(String vectorString) {
        // parse the vectorstring to float array here
        float[] float_nums = stringToVector(vectorString);
        return getNearestCentroidId(float_nums);
    }

    public ArrayList<Integer> getTopKNearestCentroidId(VectorConstant vc, int k) {
        distFn.setQueryVector(vc);
        // use priority queue to get top k nearest centroid
        // maintain a priority queue storing a group of smallest distance
        // O(nlogk) method, maybe can be improved to O(n) method (QuickSelect)
        // If k is small, this method is good enough
        // Comparator<CustomPair<Double, Integer>> comparator =
        // Comparator.comparing(CustomPair::getFirst);
        // PriorityQueue<CustomPair<Double, Integer>> pq = new
        // PriorityQueue<>(comparator);
        PriorityQueue<CustomPair<Double, Integer>> pq = new PriorityQueue<CustomPair<Double, Integer>>(k,
                new CustomPairComparator());
        for (int j = 0; j < numOfCluster; j++) {
            VectorConstant tempConstant = new VectorConstant(centroids.get(j));
            double temp_dis = distFn.distance(tempConstant);
            pq.add(new CustomPair<Double, Integer>(temp_dis, j));
            if (pq.size() > k) {
                pq.poll();
            }
        }

        // ArrayList<Integer> result = new ArrayList<>();
        // while (!pq.isEmpty()) {
        // result.add(pq.poll().getSecond());
        // }
        // Optimized version
        ArrayList<Integer> result = pq.stream().map(CustomPair::getSecond).collect(ArrayList::new, ArrayList::add,
                ArrayList::addAll);
        return result;
    }

    public ArrayList<Integer> getTopKNearestCentroidIdConcurrently(VectorConstant vc, int k) {
        distFn.setQueryVector(vc);
        ExecutorService executor = Executors.newFixedThreadPool(numOfCluster);
        PriorityQueue<CustomPair<Double, Integer>> pq = new PriorityQueue<CustomPair<Double, Integer>>(k,
                new CustomPairComparator());
        List<Future<CustomPair<Double, Integer>>> futures = new ArrayList<>();
        for (int j = 0; j < numOfCluster; j++) {
            int finalJ = j;
            Future<CustomPair<Double, Integer>> future = executor.submit(() -> {
                VectorConstant tempConstant = new VectorConstant(centroids.get(finalJ));
                double temp_dis = distFn.distance(tempConstant);
                return new CustomPair<Double, Integer>(temp_dis, finalJ);
            });
            futures.add(future);
        }
        for (Future<CustomPair<Double, Integer>> future : futures) {
            try {
                CustomPair<Double, Integer> pair = future.get();
                pq.add(pair);
                if (pq.size() > k) {
                    pq.poll();
                }
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }

        executor.shutdown();
        ArrayList<Integer> result = pq.stream().map(CustomPair::getSecond).collect(ArrayList::new, ArrayList::add,
                ArrayList::addAll);
        return result;
    }

    public ArrayList<Integer> getTopKNearestCentroidId(float[] vector, int k) {
        // set query vector o distance function to the sample vector
        VectorConstant vc = new VectorConstant(vector);
        return getTopKNearestCentroidId(vc, k);
    }

    public ArrayList<Integer> getTopKNearestCentroidId(String vectorString, int k) {
        // parse the vectorstring to float array here
        float[] float_nums = stringToVector(vectorString);
        return getTopKNearestCentroidId(float_nums, k);
    }

    public void insertVectorIntoCluster(int clusterId, float[] vector) {
        // TODO: insert vector into corresponding cluster table
    }

    public float[] stringToVector(String vectorString) {
        // split string by space
        String[] splited = vectorString.split("\\s+");
        float[] float_nums = new float[SiftBenchConstants.NUM_DIMENSION];
        int idx = 0;
        for (String s : splited) {
            // string to float
            float f = Float.parseFloat(s);
            // add to a vector array
            float_nums[idx] = f;
            idx++;
        }
        return float_nums;
    }

    public VectorConstant stringToVectorWithReduction(String vectorString, int dimension) {
        float[] float_nums = stringToVector(vectorString);
        return reduceDim(new VectorConstant(float_nums), dimension);
    }

    public VectorConstant stringToVectorWithReductionAndNorm(String vectorString, int dimension) {
        float[] float_nums = stringToVector(vectorString);
        return normAndReduceDim(new VectorConstant(float_nums), dimension);
    }

    public ArrayList<float[]> getCentroid() {
        return centroids;
    }

    public void debugMsgOfAllCentroids() {
        System.out.println("initialed centroids =");
        for (int i = 0; i < numOfCluster; i++) {
            System.out.println("centroid" + i + " = ");
            for (int j = 0; j < N_DIM; j++) {
                System.out.print(centroids.get(i)[j] + " ");
            }
            System.out.println(" ");
        }
    }

    public void debugMsgOfOneVector(float[] vector) {
        for (int j = 0; j < SiftBenchConstants.NUM_DIMENSION; j++) {
            System.out.print(vector[j] + " ");
        }
        System.out.println(" ");
    }

    public void debugMsgOfOneVector(VectorConstant vc) {
        for (int j = 0; j < SiftBenchConstants.NUM_DIMENSION; j++) {
            System.out.print(vc.get(j) + " ");
        }
        System.out.println(" ");
    }

    public void debugEachDistance(VectorConstant vc) {
        distFn.setQueryVector(vc);
        for (int j = 0; j < numOfCluster; j++) {
            VectorConstant tempConstant = new VectorConstant(centroids.get(j));
            double temp_dis = distFn.distance(tempConstant);
            System.out.println("distance to centroid" + j + " = " + temp_dis);
        }
    }

    public VectorConstant reduceDim (VectorConstant origin, int dimension){
        float[] new_float = new float[(int)dimension/4];
        for (int i = 0;i < (int)dimension/4;i++){
            // a+b = c
            //new_float[i] = (float) ((float) Math.round((origin.get(i*4) + origin.get(i*4+1) + origin.get(i*4+2) + origin.get(i*4+3)) * 1000) / 1000);
            // sqrt(a*a+b*b) = c
            new_float[i] = (float) ((float) Math.round(Math.sqrt(Math.pow(origin.get(i*4),2) + Math.pow(origin.get(i*4+1),2) + Math.pow(origin.get(i*4 +2),2) + Math.pow(origin.get(i*4+3),2)) * 1000) / 1000);
        }
        return new VectorConstant(new_float);
    }

    public VectorConstant normAndReduceDim(VectorConstant origin, int dimension){
        // normalize
        VectorConstant normVec = (VectorConstant)((VectorConstant) origin.sub(meanOfAllDIM)).elementDiv(standOfAllDIM);
        // reduce dim
        return reduceDim(normVec, dimension);
    }

    public VectorConstant normVector(VectorConstant origin){
        VectorConstant normVec = (VectorConstant)((VectorConstant) origin.sub(meanOfAllDIM)).elementDiv(standOfAllDIM);
        float[] new_float = new float[N_DIM];
        for (int i = 0;i < N_DIM;i++){
            new_float[i] = (float) ((float) Math.round(normVec.get(i)  * 1000) / 1000);
        }
        return new VectorConstant(new_float);
    }
}
