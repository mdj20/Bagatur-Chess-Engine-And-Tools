package cfg;


import bagaturchess.engines.bagatur.cfg.search.SearchConfigImpl_AB;
import bagaturchess.search.api.ISearchConfig_MTD;
import bagaturchess.uci.api.IUCIOptionsProvider;
import bagaturchess.uci.api.IUCIOptionsRegistry;
import bagaturchess.uci.impl.commands.options.UCIOption;


public class SearchConfigImpl_MTD extends SearchConfigImpl_AB implements ISearchConfig_MTD, IUCIOptionsProvider {
	
	
	private UCIOption[] options = new UCIOption[] {
			/*new UCIOptionSpin("Search [MTD trust window]", 0.0,
					"type spin default " + 0
								+ " min " + 0
								+ " max " + 100, 1)*/
	};
	
	private int mtdTrustWindow = 0;
	
	
	public SearchConfigImpl_MTD() {
		
	}
	
	
	@Override
	public void registerProviders(IUCIOptionsRegistry registry) {
		registry.registerProvider(this);
	}
	
	
	@Override
	public UCIOption[] getSupportedOptions() {
		UCIOption[] parentOptions = super.getSupportedOptions();
		
		UCIOption[] result = new UCIOption[parentOptions.length + options.length];
		
		System.arraycopy(options, 0, result, 0, options.length);
		System.arraycopy(parentOptions, 0, result, options.length, parentOptions.length);
		
		return result;
	}
	
	
	@Override
	public boolean applyOption(UCIOption option) {
		if ("Search [MTD trust window]".equals(option.getName())) {
			mtdTrustWindow = (int) ((Double) option.getValue()).doubleValue();
			return true;
		}
		
		return super.applyOption(option);
	}
}
