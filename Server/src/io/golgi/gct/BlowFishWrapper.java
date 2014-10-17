package io.golgi.gct;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Hashtable;
import java.util.StringTokenizer;

import com.openmindnetworks.golgi.api.GolgiCrypto;
import com.openmindnetworks.golgi.api.GolgiCrypto.DecryptException;
import com.openmindnetworks.golgi.api.GolgiCrypto.EncryptHardException;
import com.openmindnetworks.golgi.api.GolgiCrypto.EncryptSoftException;

public class BlowFishWrapper implements GolgiCrypto.Impl {
	
	private Hashtable<String,GolgiCrypto.Impl> bfHash = new Hashtable<String,GolgiCrypto.Impl>();
	

	@Override
	public String decrypt(String src, String payload) throws DecryptException {
		// System.out.println("Decrypt payload from '" + src + "'");
		GolgiCrypto.Impl impl = bfHash.get(src.toLowerCase());
		if(impl == null){
			throw new DecryptException("No Key Phrase for endpoing '" + src + "'");
		}
		return impl.decrypt(src, payload);
	}

	@Override
	public String encrypt(String dst, String payload) throws EncryptSoftException, EncryptHardException {
		// System.out.println("Encrypt payload for '" + dst + "'");
		GolgiCrypto.Impl impl = bfHash.get(dst.toLowerCase());
		if(impl == null){
			throw new EncryptHardException("No Key Phrase for endpoing '" + dst + "'");
		}
		
		return impl.encrypt(dst, payload);
	}
	
	private void addScheme(String endpointName, String keyPhrase){
		bfHash.put(endpointName, GolgiBlowFish.createUsingKey(keyPhrase));
	}
	
	public BlowFishWrapper(String filename){
		int schemeCount = 0;
		int lineNo;
		String line;
		try{
			BufferedReader r = new BufferedReader(new FileReader(filename));
			lineNo = 0;
			while((line = r.readLine()) != null){
				lineNo++;
				line = line.trim();
								
				if(line.length() > 0 && line.charAt(0) != '#'){
					StringTokenizer stk = new StringTokenizer(line);
					if(stk.countTokens() != 2){
						System.err.println("Error parsing endpoint/keyPhrase on line " + lineNo + " of " + filename);
						System.exit(-1);
					}
					String endpoint = stk.nextToken().toLowerCase();
					String keyPhrase = stk.nextToken();
					bfHash.put(endpoint, GolgiBlowFish.createUsingKey(keyPhrase));
					schemeCount++;
				}
			}
		}
		catch(FileNotFoundException fnfex){
			System.err.println("Endpoint/keyPhrase file '" + filename + "' does not exist");
			System.exit(-1);
		}
		catch(IOException ioex){
		}
		if(schemeCount == 0){
			System.err.println("At least one endpoint/keyPhrase must be specified in " + filename);
			System.exit(-1);
		}
	}
	
}
